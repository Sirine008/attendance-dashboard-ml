"""Entraînement + évaluation honnête du modèle de risque d'absence.

Protocole (SPEC §6.4) :
- split temporel : entraînement sur les échantillons dont la cible se termine au plus tard le 31/12/2024,
  test sur les cibles de 2025 ;
- validation walk-forward mensuelle (juillet 2024 → mai 2025) ; la sélection du modèle se fait sur les
  seuls mois de validation 2024 (PR-AUC agrégée), le test 2025 n'intervient jamais dans un choix ;
- comparaison obligatoire à 2 baselines ; un modèle qui ne bat pas la meilleure baseline n'est pas promu ;
- calibration sigmoïde ; seuils de risque = quantiles 70 % / 90 % des probabilités de validation ;
- le modèle déployé est ré-entraîné sur toutes les données disponibles avec les mêmes réglages.

Usage : python -m pipeline.train [--no-db-register] [--synthetic-report]
"""
from __future__ import annotations

import argparse
import json
import os
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.calibration import calibration_curve
from sklearn.metrics import average_precision_score, brier_score_loss, roc_auc_score

from . import data as data_mod
from .features import build_samples
from .modeling import BASELINES, CANDIDATES, HorizonModel, baseline_model, fit_candidate, predict_any

ROOT = Path(__file__).resolve().parents[1]
MODEL_DIR = Path(os.environ.get("MODEL_DIR", ROOT / "models"))
REPORT_DIR = ROOT / "reports"
SPLIT_DATE = pd.Timestamp("2025-01-01")
VALIDATION_MONTHS = pd.period_range("2024-07", "2024-12", freq="M")
ALL_KINDS = CANDIDATES + BASELINES


# --------------------------------------------------------------------------- métriques

def precision_at_k(df: pd.DataFrame, proba: np.ndarray, k: int = 3) -> float | None:
    """Pour chaque jour cible : parmi les k collaborateurs les plus à risque, part réellement absente."""
    d = df.assign(p=proba)
    scores = [g.nlargest(k, "p")["y"].mean() for _, g in d.groupby("target_date") if len(g) >= k]
    return float(np.mean(scores)) if scores else None


def metrics(df: pd.DataFrame, proba: np.ndarray, thresholds: dict | None = None) -> dict:
    y = df["y"].to_numpy(dtype=int)
    out = {"n": int(len(y)), "positives": int(y.sum()), "base_rate": round(float(y.mean()), 4)}
    if 0 < y.sum() < len(y):
        out["pr_auc"] = round(float(average_precision_score(y, proba)), 4)
        out["roc_auc"] = round(float(roc_auc_score(y, proba)), 4)
    out["brier"] = round(float(brier_score_loss(y, proba)), 4)
    pk = precision_at_k(df, proba)
    out["precision_at_3"] = round(pk, 4) if pk is not None else None
    if thresholds:
        for level in ("MODERE", "ELEVE"):
            flagged = proba >= thresholds[level]
            out[f"recall_{level.lower()}"] = round(float(flagged[y == 1].mean()), 4) if y.sum() else None
            out[f"precision_{level.lower()}"] = round(float(y[flagged].mean()), 4) if flagged.any() else None
            out[f"flagged_share_{level.lower()}"] = round(float(flagged.mean()), 4)
    return out


# --------------------------------------------------------------------------- protocole

@dataclass
class HorizonResult:
    horizon: str
    chosen: str
    promoted_model: bool
    validation: dict
    test: dict
    walk_forward: pd.DataFrame
    thresholds: dict
    test_frame: pd.DataFrame
    test_proba: dict
    model: HorizonModel
    importance: pd.Series
    n_train: int
    n_test: int


def walk_forward(samples: pd.DataFrame, teams: list[str], horizon: str, months) -> tuple[pd.DataFrame, dict[str, pd.DataFrame]]:
    rows, oof = [], {k: [] for k in ALL_KINDS}
    for m in months:
        start = m.start_time
        train = samples[samples.target_end < start]
        test = samples[samples.target_date.dt.to_period("M") == m]
        if test.empty or train.y.sum() < 10:
            continue
        for kind in ALL_KINDS:
            p = predict_any(kind, train, test, teams, horizon)
            oof[kind].append(test.assign(p=p))
            r = metrics(test, p)
            rows.append({"month": str(m), "model": kind, **r})
    return pd.DataFrame(rows), {k: pd.concat(v) for k, v in oof.items() if v}


def run_horizon(ds: data_mod.Dataset, horizon: str, log=print) -> HorizonResult:
    samples = build_samples(ds, horizon)
    teams = sorted(t for t in samples["team"].dropna().unique())
    all_months = pd.period_range(VALIDATION_MONTHS[0], samples.target_date.max().to_period("M"), freq="M")
    wf, oof = walk_forward(samples, teams, horizon, all_months)

    # sélection sur les mois de validation 2024 uniquement
    val = {k: v[v.target_date.dt.to_period("M").isin(VALIDATION_MONTHS)] for k, v in oof.items()}
    validation = {k: metrics(v, v["p"].to_numpy()) for k, v in val.items()}
    best_model = max(CANDIDATES, key=lambda k: validation[k].get("pr_auc", 0))
    best_baseline = max(BASELINES, key=lambda k: validation[k].get("pr_auc", 0))
    promoted = validation[best_model].get("pr_auc", 0) > validation[best_baseline].get("pr_auc", 0)
    chosen = best_model if promoted else best_baseline
    log(f"[{horizon}] validation PR-AUC : " + ", ".join(f"{k}={validation[k].get('pr_auc')}" for k in ALL_KINDS)
        + f" -> retenu : {chosen}{'' if promoted else ' (aucun modèle ne bat la baseline)'}")

    vp = val[chosen]["p"].to_numpy()
    thresholds = {"MODERE": round(float(np.quantile(vp, 0.70)), 4), "ELEVE": round(float(np.quantile(vp, 0.90)), 4)}
    if thresholds["ELEVE"] <= thresholds["MODERE"]:
        thresholds["ELEVE"] = round(thresholds["MODERE"] + 1e-4, 4)

    # test 2025 (modèles entraînés jusqu'au 31/12/2024)
    train = samples[samples.target_end < SPLIT_DATE]
    test = samples[samples.target_date >= SPLIT_DATE]
    test_proba = {k: predict_any(k, train, test, teams, horizon) for k in ALL_KINDS}
    test_metrics = {k: metrics(test, p, thresholds if k == chosen else None) for k, p in test_proba.items()}
    for k in ALL_KINDS:
        prob_true, prob_pred = calibration_curve(test["y"], test_proba[k], n_bins=5, strategy="quantile")
        test_metrics[k]["calibration_curve"] = {"predicted": [round(float(x), 4) for x in prob_pred],
                                                "observed": [round(float(x), 4) for x in prob_true]}

    # modèle déployé : ré-entraîné sur toutes les données
    if promoted:
        final = fit_candidate(chosen, samples, teams, horizon)
    else:
        final = baseline_model(chosen, samples, teams, horizon)
    final.thresholds = thresholds
    importance = _importance(final, samples)
    return HorizonResult(horizon, chosen, promoted, validation, test_metrics, wf, thresholds, test, test_proba, final,
                         importance, len(train), len(test))


def _importance(model: HorizonModel, samples: pd.DataFrame) -> pd.Series:
    from .explain import grouped
    contrib = model.contributions(samples)
    mean_abs = contrib.abs().mean()
    return pd.Series(grouped(mean_abs)).sort_values(ascending=False)


# --------------------------------------------------------------------------- persistance

def save_artifact(results: dict[str, HorizonResult], ds: data_mod.Dataset) -> dict:
    version = "v" + datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    data_end = ds.daily.work_date.max().date().isoformat()
    summary = {
        "version": version,
        "trained_at": datetime.now(timezone.utc).isoformat(),
        "data": {"employees": int(ds.employees.shape[0]), "start": ds.daily.work_date.min().date().isoformat(), "end": data_end,
                 "synthetic": False},
        "horizons": {h: {"algorithm": r.chosen, "promoted_model": r.promoted_model, "thresholds": r.thresholds,
                         "validation": r.validation, "test": r.test, "n_train": r.n_train, "n_test": r.n_test,
                         "expected_days_factor": r.model.expected_days_factor}
                     for h, r in results.items()},
    }
    artifact = {"version": version, "summary": summary, "models": {h: r.model for h, r in results.items()}}
    joblib.dump(artifact, MODEL_DIR / f"{version}.joblib", compress=3)
    (MODEL_DIR / "ACTIVE").write_text(version, encoding="utf-8")
    (MODEL_DIR / f"{version}.json").write_text(json.dumps(summary, indent=2, ensure_ascii=False), encoding="utf-8")
    return summary


def register(eng, summary: dict) -> None:
    from sqlalchemy import text
    algo = " / ".join(f"{h}: {v['algorithm']}" for h, v in summary["horizons"].items())
    thresholds = {h: v["thresholds"] for h, v in summary["horizons"].items()}
    with eng.begin() as c:
        c.execute(text("UPDATE model_registry SET is_active = FALSE WHERE is_active"))
        c.execute(text("""INSERT INTO model_registry (version, trained_at, algorithm, metrics, thresholds, is_active, notes)
                          VALUES (:v, :t, :a, CAST(:m AS jsonb), CAST(:th AS jsonb), TRUE, :n)"""),
                  {"v": summary["version"], "t": summary["trained_at"], "a": algo,
                   "m": json.dumps(summary, ensure_ascii=False), "th": json.dumps(thresholds), "n": "Métriques officielles : données réelles uniquement"})


def train_and_save(eng, log=print, write_report: bool = True, register_db: bool = True) -> dict:
    ds = data_mod.load(eng).subset(synthetic=False)
    results = {h: run_horizon(ds, h, log) for h in ("D1", "W1")}
    summary = save_artifact(results, ds)
    if write_report:
        from .report import write_model_card
        write_model_card(results, summary, ds, REPORT_DIR)
    if register_db:
        register(eng, summary)
    log(f"Modèle {summary['version']} enregistré")
    return summary


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--no-db-register", action="store_true")
    ap.add_argument("--synthetic-report", action="store_true", help="expérience séparée sur données synthétiques")
    args = ap.parse_args()
    eng = data_mod.engine()
    if args.synthetic_report:
        from .synthetic_eval import run as run_synthetic
        run_synthetic(REPORT_DIR)
        return
    train_and_save(eng, register_db=not args.no_db_register)


if __name__ == "__main__":
    main()
