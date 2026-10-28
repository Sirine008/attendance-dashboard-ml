"""Chargement des données d'assiduité (base PostgreSQL alimentée par le backend)."""
from __future__ import annotations

import os
from dataclasses import dataclass, field
from datetime import date

import pandas as pd
from sqlalchemy import create_engine, text
from sqlalchemy.engine import Engine

DEFAULT_DB_URL = "postgresql+psycopg://assiduite:assiduite@localhost:5440/assiduite"


@dataclass
class Dataset:
    """Panel d'assiduité. `daily` : 1 ligne par collaborateur × jour (colonnes de `daily_attendance`)."""

    employees: pd.DataFrame  # id, code, team_id, team_name, hire_date, is_synthetic
    daily: pd.DataFrame
    holidays: set[date]
    periods: list[tuple[str, date, date]] = field(default_factory=list)

    def subset(self, synthetic: bool | None) -> "Dataset":
        if synthetic is None:
            return self
        emps = self.employees[self.employees.is_synthetic == synthetic]
        return Dataset(emps, self.daily[self.daily.employee_id.isin(emps.id)], self.holidays, self.periods)


def engine(url: str | None = None) -> Engine:
    return create_engine(url or os.environ.get("DATABASE_URL", DEFAULT_DB_URL), pool_pre_ping=True)


def load(eng: Engine) -> Dataset:
    with eng.connect() as c:
        employees = pd.read_sql(text("""
            SELECT e.id, e.code, e.team_id, t.name AS team_name, e.hire_date, e.is_synthetic
            FROM employee e LEFT JOIN team t ON t.id = e.team_id ORDER BY e.id"""), c)
        daily = pd.read_sql(text("""
            SELECT employee_id, work_date, working_day, status, first_in, late_minutes, worked_minutes,
                   incomplete, reference_start
            FROM daily_attendance ORDER BY employee_id, work_date"""), c)
        holidays = {r[0] for r in c.execute(text("SELECT holiday_date FROM public_holiday"))}
        periods = [(r[0], r[1], r[2]) for r in c.execute(text("SELECT kind, start_date, end_date FROM special_period"))]
    return normalize(Dataset(employees, daily, holidays, periods))


def normalize(ds: Dataset) -> Dataset:
    d = ds.daily.copy()
    d["work_date"] = pd.to_datetime(d["work_date"])
    for col in ("first_in", "reference_start"):
        d[col + "_min"] = d[col].map(_to_minutes) if col in d else float("nan")
    e = ds.employees.copy()
    e["hire_date"] = pd.to_datetime(e["hire_date"])
    return Dataset(e, d, set(ds.holidays), list(ds.periods))


def _to_minutes(t) -> float:
    if t is None or (isinstance(t, float) and pd.isna(t)) or t is pd.NaT:
        return float("nan")
    if isinstance(t, str):
        h, m, *s = t.split(":")
        return int(h) * 60 + int(m) + (int(s[0]) / 60 if s else 0)
    return t.hour * 60 + t.minute + t.second / 60
