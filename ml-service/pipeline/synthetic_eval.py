"""Expérience séparée sur données SYNTHÉTIQUES (même protocole que les données réelles).

Ces métriques ne sont PAS les métriques officielles du modèle : elles montrent le comportement du pipeline
sur un volume plus grand, avec des régularités connues (car simulées). → reports/synthetic_report.md
"""
from __future__ import annotations

import json
from pathlib import Path

from synthetic.generator import DEFAULT_PARAMS, generate

from .calendar_tn import PUBLIC_HOLIDAYS, SPECIAL_PERIODS
from .data import Dataset, normalize
from .train import ALL_KINDS, run_horizon

NAMES = {"logistic": "Régression logistique", "lgbm": "LightGBM", "baseline_hist": "Baseline (a) taux historique",
         "baseline_dow": "Baseline (b) taux jour de semaine"}


def run(out: Path, n: int = 60, seed: int = 42) -> dict:
    params = DEFAULT_PARAMS
    calib = out / "synthetic_calibration.json"
    if calib.exists():
        params = json.loads(calib.read_text(encoding="utf-8"))
    emps, daily, _ = generate(n, seed, params)
    ds = normalize(Dataset(emps, daily, set(PUBLIC_HOLIDAYS), SPECIAL_PERIODS))
    results = {h: run_horizon(ds, h, print) for h in ("D1", "W1")}
    L = ["# Expérience sur données SYNTHÉTIQUES\n",
         "> ⚠️ **Données générées** (`ml-service/synthetic/generator.py`, calibré sur les statistiques réelles). "
         "Ces chiffres **ne sont pas** les performances du modèle sur les vraies données : voir `model_card.md`.\n",
         f"- {n} collaborateurs synthétiques, graine {seed}, même période (01/2024 → 05/2025), même protocole "
         "(split temporel au 31/12/2024, walk-forward, baselines, calibration).",
         "- Le générateur simule explicitement l'effet jour de semaine, veille / lendemain de férié et une propension "
         "individuelle : un modèle qui retrouve ces régularités doit donc battre les baselines ici.\n"]
    summary = {}
    for h, r in results.items():
        L.append(f"## Horizon {h}\n")
        L.append(f"Modèle retenu en validation : **{NAMES[r.chosen]}**{'' if r.promoted_model else ' (aucun modèle ne bat la baseline)'}.\n")
        L.append("| Modèle | PR-AUC test | ROC-AUC test | Brier test | Précision@3 test |\n|---|---|---|---|---|")
        for k in ALL_KINDS:
            m = r.test[k]
            L.append(f"| {NAMES[k]} | {m.get('pr_auc', 0):.3f} | {m.get('roc_auc', 0):.3f} | {m['brier']:.3f} | {(m['precision_at_3'] or 0):.3f} |")
        L.append(f"\nTaux de positifs en test : {r.test[r.chosen]['base_rate']:.1%} ({r.n_test} échantillons).\n")
        summary[h] = {"chosen": r.chosen, "test": {k: {kk: v for kk, v in r.test[k].items() if kk != 'calibration_curve'} for k in ALL_KINDS}}
    out.mkdir(parents=True, exist_ok=True)
    (out / "synthetic_report.md").write_text("\n".join(L) + "\n", encoding="utf-8")
    (out / "synthetic_metrics.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    return summary
