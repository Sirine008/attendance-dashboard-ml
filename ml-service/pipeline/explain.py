"""Traduction des contributions en phrases lisibles.

Formulation imposée : il s'agit de facteurs **associés** à l'estimation, jamais de causes.
"""
from __future__ import annotations

import math

import pandas as pd

DAYS = ["lundi", "mardi", "mercredi", "jeudi", "vendredi"]
MONTHS = ["janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre",
          "novembre", "décembre"]
# indicateurs pour lesquels une valeur nulle n'est pas mentionnée (« pas veille de férié » n'aide personne)
SKIP_IF_ZERO = {"veille_ferie", "lendemain_ferie", "pont", "seance_unique", "ramadan", "absent_at_ref", "current_streak",
                "abs_p_7", "late_7", "incomplete_30"}


def _plural(n: float, word: str, plural: str | None = None) -> str:
    n = int(round(n))
    return f"{n} {word if n <= 1 else (plural or word + 's')}"


def phrase(feature: str, row: pd.Series, horizon: str) -> str | None:
    v = row.get(feature)
    w1 = horizon == "W1"
    match feature:
        case "abs_p_7" | "abs_p_30" | "abs_p_90":
            d = feature.split("_")[-1]
            return f"{_plural(v, 'absence ponctuelle', 'absences ponctuelles')} sur les {d} derniers jours"
        case "abs_l_90":
            return f"{_plural(v, 'jour')} d'absence longue sur les 90 derniers jours"
        case "days_since_abs_p":
            return "aucune absence ponctuelle récente" if v >= 250 else f"dernière absence ponctuelle il y a {_plural(v, 'jour ouvré', 'jours ouvrés')}"
        case "current_streak":
            return f"absent depuis {_plural(v, 'jour ouvré', 'jours ouvrés')} au dernier jour connu"
        case "absent_at_ref":
            return "absent le dernier jour connu"
        case "hist_rate":
            return f"taux historique d'absence ponctuelle : {v:.0%}"
        case "dow_rate":
            if w1:
                return f"taux historique d'absence sur les jours de la semaine visée : {v:.0%}"
            dow = next((k for k in range(5) if row.get(f"dow_{k}") == 1), None)
            return f"taux historique d'absence le {DAYS[dow]} : {v:.0%}" if dow is not None else f"taux historique pour ce jour : {v:.0%}"
        case "late_7" | "late_30":
            return f"{_plural(v, 'retard')} sur les {feature.split('_')[1]} derniers jours"
        case "late_min_mean_30":
            return f"retard moyen de {v:.0f} min par jour sur 30 jours"
        case "rel_arrival_30":
            return f"arrivée moyenne {v:+.0f} min par rapport à l'horaire de référence (30 j)"
        case "arrival_slope_30":
            return f"heure d'arrivée {'de plus en plus tardive' if v > 0 else 'de plus en plus matinale'} sur 30 jours"
        case "hours_mean_7" | "hours_mean_30":
            if v is None or (isinstance(v, float) and math.isnan(v)):
                return None
            return f"{v:.1f} h travaillées en moyenne sur {feature.split('_')[-1]} jours"
        case "hours_slope_30":
            return f"heures travaillées {'en hausse' if v > 0 else 'en baisse'} sur 30 jours"
        case "incomplete_30":
            return f"{_plural(v, 'journée')} de pointage incomplet sur 30 jours"
        case "n_history":
            return f"historique de {_plural(v, 'jour ouvré', 'jours ouvrés')}"
        case "seniority_years":
            return None if v is None or (isinstance(v, float) and math.isnan(v)) else f"ancienneté de {v:.1f} an(s)"
        case "veille_ferie":
            return f"{_plural(v, 'veille')} de jour férié dans la semaine" if w1 else "veille de jour férié"
        case "lendemain_ferie":
            return f"{_plural(v, 'lendemain')} de jour férié dans la semaine" if w1 else "lendemain de jour férié"
        case "pont":
            return "pont potentiel dans la semaine" if w1 else "jour de pont potentiel"
        case "days_to_holiday":
            return f"prochain jour férié dans {_plural(v, 'jour')}"
        case "seance_unique":
            return "période de séance unique (été)"
        case "ramadan":
            return "période de Ramadan"
        case "gap_days":
            return f"reprise après {int(v)} jours sans travail (week-end / férié)" if v > 1 else "lendemain d'un jour travaillé"
        case "dow":
            dow = next((k for k in range(5) if row.get(f"dow_{k}") == 1), None)
            if dow is None:
                return None
            return f"semaine commençant un {DAYS[dow]}" if w1 else f"jour visé : {DAYS[dow]}"
        case "month":
            m = int(round((math.atan2(row["month_sin"], row["month_cos"]) / (2 * math.pi) * 12))) % 12 or 12
            return f"mois de {MONTHS[m - 1]}"
        case "team":
            team = row.get("team")
            return f"équipe : {team}" if team else None
    return None


def grouped(contrib: pd.Series) -> dict[str, float]:
    """Regroupe les indicatrices (jour, mois, équipe) en un seul facteur."""
    out: dict[str, float] = {}
    for name, c in contrib.items():
        key = ("dow" if name[:4] == "dow_" and name[4:].isdigit()
               else "month" if name in ("month_sin", "month_cos")
               else "team" if name.startswith("team__") else name)
        out[key] = out.get(key, 0.0) + float(c)
    return out


def top_factors(row: pd.Series, contrib: pd.Series, horizon: str, k: int = 3) -> list[dict]:
    factors = []
    for name, c in sorted(grouped(contrib).items(), key=lambda kv: -abs(kv[1])):
        if abs(c) < 1e-6:
            continue
        if name in SKIP_IF_ZERO and not row.get(name):
            continue
        text = phrase(name, row, horizon)
        if not text:
            continue
        factors.append({"feature": name, "label": text, "direction": "UP" if c > 0 else "DOWN", "weight": round(abs(c), 4)})
        if len(factors) == k:
            break
    return factors
