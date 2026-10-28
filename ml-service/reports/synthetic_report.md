# Expérience sur données SYNTHÉTIQUES

> ⚠️ **Données générées** (`ml-service/synthetic/generator.py`, calibré sur les statistiques réelles). Ces chiffres **ne sont pas** les performances du modèle sur les vraies données : voir `model_card.md`.

- 60 collaborateurs synthétiques, graine 42, même période (01/2024 → 05/2025), même protocole (split temporel au 31/12/2024, walk-forward, baselines, calibration).
- Le générateur simule explicitement l'effet jour de semaine, veille / lendemain de férié et une propension individuelle : un modèle qui retrouve ces régularités doit donc battre les baselines ici.

## Horizon D1

Modèle retenu en validation : **LightGBM**.

| Modèle | PR-AUC test | ROC-AUC test | Brier test | Précision@3 test |
|---|---|---|---|---|
| Régression logistique | 0.221 | 0.784 | 0.057 | 0.226 |
| LightGBM | 0.213 | 0.772 | 0.055 | 0.226 |
| Baseline (a) taux historique | 0.107 | 0.648 | 0.059 | 0.150 |
| Baseline (b) taux jour de semaine | 0.130 | 0.664 | 0.059 | 0.136 |

Taux de positifs en test : 6.4% (5340 échantillons).

## Horizon W1

Modèle retenu en validation : **Baseline (b) taux jour de semaine** (aucun modèle ne bat la baseline).

| Modèle | PR-AUC test | ROC-AUC test | Brier test | Précision@3 test |
|---|---|---|---|---|
| Régression logistique | 0.441 | 0.701 | 0.169 | 0.457 |
| LightGBM | 0.431 | 0.706 | 0.174 | 0.468 |
| Baseline (a) taux historique | 0.389 | 0.679 | 0.169 | 0.487 |
| Baseline (b) taux jour de semaine | 0.395 | 0.681 | 0.168 | 0.468 |

Taux de positifs en test : 23.9% (5118 échantillons).

