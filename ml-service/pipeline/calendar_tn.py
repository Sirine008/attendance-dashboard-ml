"""Calendrier tunisien de référence (miroir du seed Flyway `V2__seed_reference.sql`).

Les dates religieuses sont celles annoncées officiellement / observées dans les données ;
elles restent marquées « à vérifier » dans DECISIONS.md.
"""
from __future__ import annotations

from datetime import date

PUBLIC_HOLIDAYS: dict[date, str] = {
    # 2024
    date(2024, 1, 1): "Jour de l'An",
    date(2024, 3, 20): "Fête de l'Indépendance",
    date(2024, 4, 9): "Journée des Martyrs",
    date(2024, 4, 10): "Aïd el-Fitr",
    date(2024, 4, 11): "Aïd el-Fitr (2e jour)",
    date(2024, 5, 1): "Fête du Travail",
    date(2024, 6, 16): "Aïd el-Adha",
    date(2024, 6, 17): "Aïd el-Adha (2e jour)",
    date(2024, 7, 7): "Ras El Am El Hejri",
    date(2024, 7, 25): "Fête de la République",
    date(2024, 8, 13): "Fête de la Femme",
    date(2024, 9, 15): "Mouled",
    date(2024, 10, 15): "Fête de l'Évacuation",
    date(2024, 12, 17): "Fête de la Révolution",
    # 2025
    date(2025, 1, 1): "Jour de l'An",
    date(2025, 3, 20): "Fête de l'Indépendance",
    date(2025, 3, 30): "Aïd el-Fitr",
    date(2025, 3, 31): "Aïd el-Fitr (2e jour)",
    date(2025, 4, 1): "Aïd el-Fitr (3e jour)",
    date(2025, 4, 9): "Journée des Martyrs",
    date(2025, 5, 1): "Fête du Travail",
    date(2025, 6, 6): "Aïd el-Adha",
    date(2025, 6, 7): "Aïd el-Adha (2e jour)",
    date(2025, 6, 26): "Ras El Am El Hejri",
    date(2025, 7, 25): "Fête de la République",
    date(2025, 8, 13): "Fête de la Femme",
    date(2025, 9, 4): "Mouled",
    date(2025, 10, 15): "Fête de l'Évacuation",
    date(2025, 12, 17): "Fête de la Révolution",
}

# (kind, début, fin) — bornes incluses
SPECIAL_PERIODS: list[tuple[str, date, date]] = [
    ("RAMADAN", date(2024, 3, 11), date(2024, 4, 9)),
    ("SEANCE_UNIQUE", date(2024, 7, 1), date(2024, 8, 31)),
    ("RAMADAN", date(2025, 3, 1), date(2025, 3, 29)),
    ("SEANCE_UNIQUE", date(2025, 7, 1), date(2025, 8, 31)),
]


def is_working_day(d: date, holidays: set[date] | None = None) -> bool:
    hol = holidays if holidays is not None else PUBLIC_HOLIDAYS.keys()
    return d.weekday() < 5 and d not in hol


def period_kind(d: date, periods=None) -> str | None:
    for kind, start, end in periods if periods is not None else SPECIAL_PERIODS:
        if start <= d <= end:
            return kind
    return None
