"""Génération de `reports/model_card.md` et des graphiques associés (résultats d'une exécution réelle)."""
from __future__ import annotations

from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
import numpy as np  # noqa: E402
import pandas as pd  # noqa: E402
from sklearn.metrics import precision_recall_curve  # noqa: E402

RED, GOLD, GREY, DARK = "#D71920", "#F6A800", "#8A8F98", "#2B2F36"
COLORS = {"logistic": RED, "lgbm": DARK, "baseline_hist": GREY, "baseline_dow": GOLD}
NAMES = {"logistic": "Régression logistique", "lgbm": "LightGBM", "baseline_hist": "Baseline (a) taux historique",
         "baseline_dow": "Baseline (b) taux jour de semaine"}
HORIZON = {"D1": "J+1 (prochain jour ouvré)", "W1": "Semaine (5 prochains jours ouvrés)"}


def _fmt(v) -> str:
    if v is None or (isinstance(v, float) and np.isnan(v)):
        return "—"
    return f"{v:.3f}" if isinstance(v, float) else str(v)


def _plots(results, out: Path) -> None:
    for h, r in results.items():
        y = r.test_frame["y"].to_numpy()
        fig, axes = plt.subplots(1, 2, figsize=(10, 4))
        for kind, p in r.test_proba.items():
            prec, rec, _ = precision_recall_curve(y, p)
            axes[0].plot(rec, prec, label=f"{NAMES[kind]} (AP={r.test[kind].get('pr_auc', 0):.2f})", color=COLORS[kind])
            cc = r.test[kind]["calibration_curve"]
            axes[1].plot(cc["predicted"], cc["observed"], marker="o", label=NAMES[kind], color=COLORS[kind])
        axes[0].axhline(y.mean(), ls=":", color="#999", label=f"Taux de base ({y.mean():.2f})")
        axes[0].set(xlabel="Rappel", ylabel="Précision", title=f"Précision-rappel — test 2025 — {h}")
        axes[0].legend(fontsize=7)
        lim = max(0.05, max(max(r.test[k]["calibration_curve"]["predicted"] + r.test[k]["calibration_curve"]["observed"]) for k in r.test_proba))
        axes[1].plot([0, lim], [0, lim], ls="--", color="#999")
        axes[1].set(xlabel="Probabilité prédite (moyenne du groupe)", ylabel="Fréquence observée", title=f"Calibration — test 2025 — {h}")
        axes[1].legend(fontsize=7)
        fig.tight_layout()
        fig.savefig(out / f"pr_calibration_{h}.png", dpi=110)
        plt.close(fig)

        wf = r.walk_forward.pivot(index="month", columns="model", values="pr_auc")
        fig, ax = plt.subplots(figsize=(9, 3.4))
        for kind in wf.columns:
            ax.plot(wf.index, wf[kind], marker="o", label=NAMES[kind], color=COLORS[kind])
        ax.set(ylabel="PR-AUC", title=f"Walk-forward mensuel — {h} (mois ≤ 12/2024 = validation, 2025 = test)")
        ax.tick_params(axis="x", rotation=45)
        ax.legend(fontsize=7)
        fig.tight_layout()
        fig.savefig(out / f"walk_forward_{h}.png", dpi=110)
        plt.close(fig)

        imp = r.importance.head(12)[::-1]
        fig, ax = plt.subplots(figsize=(6, 4))
        ax.barh(imp.index, imp.values, color=RED)
        ax.set(title=f"Importance moyenne |contribution| — {h} ({NAMES[r.chosen]})", xlabel="log-odds")
        fig.tight_layout()
        fig.savefig(out / f"importance_{h}.png", dpi=110)
        plt.close(fig)


def write_model_card(results: dict, summary: dict, ds, out: Path) -> None:
    out.mkdir(parents=True, exist_ok=True)
    _plots(results, out)
    L: list[str] = []
    w = L.append
    w(f"# Model card — risque d'absence ponctuelle (version `{summary['version']}`)\n")
    w("> Généré automatiquement par `python -m pipeline.train` à partir d'une exécution réelle. Ne pas éditer à la main.\n")
    w("## Usage prévu et usages interdits\n")
    w("- **Usage prévu** : outil d'aide à l'anticipation pour l'organisation du travail (planification, continuité de service) par les RH et les responsables N+1.")
    w("- **Usages interdits** : évaluation individuelle, notation, sanction, décision RH automatisée, toute utilisation disciplinaire. "
      "Les facteurs affichés sont des facteurs **associés** à l'estimation, jamais des causes.")
    w("- Aucune estimation n'est affichée pour un collaborateur ayant moins de 30 jours ouvrés d'historique.")
    w("- Conformité : loi organique tunisienne n° 2004-63 (protection des données personnelles) — minimisation (seuls les pointages sont utilisés), accès restreint par rôle, traçabilité des consultations.\n")
    w("## Données\n")
    d = summary["data"]
    w(f"- Données **réelles uniquement** : {d['employees']} collaborateurs, journées du {d['start']} au {d['end']} (`daily_attendance`).")
    w("- Cible D1 : `ABSENCE_PONCTUELLE` au prochain jour ouvré. Cible W1 : au moins une absence ponctuelle dans les 5 prochains jours ouvrés (modèle direct).")
    w("- Exclus des échantillons : jours cibles en congé déclaré ou en absence longue (≥ 3 jours), week-ends, fériés ; échantillons avec moins de 20 jours d'historique.")
    w("- Split temporel : entraînement = cibles ≤ 31/12/2024 ; test = cibles en 2025. Validation walk-forward mensuelle (sélection sur juillet–décembre 2024 uniquement).\n")
    for h, r in results.items():
        hs = summary["horizons"][h]
        w(f"## Horizon {HORIZON[h]}\n")
        w(f"- Échantillons : {r.n_train} en entraînement, {r.n_test} en test (taux de positifs test : {r.test[r.chosen]['base_rate']:.1%}).")
        verdict = (f"**{NAMES[r.chosen]}** promu : il bat la meilleure baseline en validation."
                   if r.promoted_model else
                   f"**Aucun modèle ne bat la meilleure baseline en validation** : c'est la baseline **{NAMES[r.chosen]}** qui est utilisée en production (règle de non-promotion).")
        w(f"- Modèle retenu : {verdict}")
        w(f"- Seuils de risque (probabilités calibrées, quantiles 70 % / 90 % de la validation) : MODÉRÉ ≥ {r.thresholds['MODERE']:.3f}, ÉLEVÉ ≥ {r.thresholds['ELEVE']:.3f}.")
        if hs.get("expected_days_factor"):
            w(f"- Nombre de jours d'absence attendu = probabilité × {hs['expected_days_factor']:.2f} (nombre moyen de jours quand la semaine contient au moins une absence, en entraînement).")
        w("\n### Validation walk-forward (juillet–décembre 2024, agrégée)\n")
        w("| Modèle | PR-AUC | ROC-AUC | Brier | Précision@3 |\n|---|---|---|---|---|")
        for k, m in r.validation.items():
            w(f"| {NAMES[k]} | {_fmt(m.get('pr_auc'))} | {_fmt(m.get('roc_auc'))} | {_fmt(m.get('brier'))} | {_fmt(m.get('precision_at_3'))} |")
        w("\n### Test 2025 (modèles entraînés sur 2024)\n")
        w("| Modèle | PR-AUC | ROC-AUC | Brier | Précision@3 |\n|---|---|---|---|---|")
        for k, m in r.test.items():
            mark = " ← retenu" if k == r.chosen else ""
            w(f"| {NAMES[k]}{mark} | {_fmt(m.get('pr_auc'))} | {_fmt(m.get('roc_auc'))} | {_fmt(m.get('brier'))} | {_fmt(m.get('precision_at_3'))} |")
        t = r.test[r.chosen]
        w(f"\nAu seuil ÉLEVÉ, le modèle retenu signale {t['flagged_share_eleve']:.1%} des cas de test, avec un rappel de "
          f"{_fmt(t['recall_eleve'])} et une précision de {_fmt(t['precision_eleve'])} ; au seuil MODÉRÉ : rappel {_fmt(t['recall_modere'])}, précision {_fmt(t['precision_modere'])}.\n")
        w(f"![PR et calibration {h}](pr_calibration_{h}.png)\n")
        w(f"![Walk-forward {h}](walk_forward_{h}.png)\n")
        w(f"![Importance {h}](importance_{h}.png)\n")
        wf = r.walk_forward.pivot(index="month", columns="model", values="pr_auc").round(3)
        wf.columns = [NAMES[c] for c in wf.columns]
        pos = r.walk_forward.drop_duplicates("month").set_index("month")["positives"]
        wf.insert(0, "positifs", pos)
        w("PR-AUC mensuelle (walk-forward) :\n")
        w("| Mois | " + " | ".join(str(c) for c in wf.columns) + " |\n|" + "---|" * (len(wf.columns) + 1))
        for idx, row in wf.iterrows():
            w(f"| {idx} | " + " | ".join(_fmt(v) if not isinstance(v, (int, np.integer)) else str(v) for v in row.values) + " |")
        w("")
    w("## Limites connues\n")
    w("- **Très petit échantillon** (8 personnes, ~120 jours d'absence ponctuelle au total) : les métriques ont une forte variance, d'un mois à l'autre comme d'un modèle à l'autre. Les écarts de quelques points de PR-AUC ne sont pas significatifs.")
    w("- Les absences « longues » sont une heuristique (≥ 3 jours ouvrés consécutifs sans pointage = congé probable) faute de données de congés : certaines absences ponctuelles peuvent être des congés posés d'un ou deux jours.")
    w("- La hiérarchie, les équipes et 6 dates d'embauche sur 8 sont fictives : les features d'ancienneté et d'équipe n'ont pas de valeur explicative réelle.")
    w("- Les probabilités sont calibrées sur une période passée ; une dérive (nouvelle organisation, télétravail, recrutements) nécessite un ré-entraînement (bouton « Réentraîner »).")
    w("- Les contributions SHAP / coefficients sont calculés sur le modèle de base non calibré ; elles indiquent un sens d'association, pas un effet causal.\n")
    w("## Données synthétiques\n")
    w("Les métriques ci-dessus sont les **seules métriques officielles**. L'expérience sur données synthétiques (60 collaborateurs générés) est rapportée à part dans `synthetic_report.md` et ne doit pas être confondue avec ces résultats.")
    (out / "model_card.md").write_text("\n".join(L) + "\n", encoding="utf-8")
