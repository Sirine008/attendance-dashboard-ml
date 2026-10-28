"""Construction des échantillons (features + cibles) — sans fuite temporelle.

Convention : une prédiction est faite à la **date de référence R** (dernière journée de données connue).
- D1 : cible = prochain jour ouvré T après R ;
- W1 : cible = les 5 prochains jours ouvrés après R.

Toutes les features d'historique n'utilisent que les journées ≤ R. En particulier, la classification
ponctuelle / longue de la série d'absence **en cours à R** est recalculée avec sa longueur connue à R
(une série de 2 jours à R peut devenir « longue » le lendemain : on ne le sait pas encore).
Les features calendaires portent sur T (le calendrier des fériés est connu à l'avance).
"""
from __future__ import annotations

import math
from dataclasses import dataclass
from datetime import date, timedelta

import numpy as np
import pandas as pd

from .data import Dataset

ABS_P, ABS_L, CONGE = "ABSENCE_PONCTUELLE", "ABSENCE_LONGUE", "CONGE"
LONG_MIN_DAYS = 3
MIN_TRAIN_HISTORY = 20       # jours ouvrés d'historique minimum pour un échantillon d'entraînement
MIN_DISPLAY_HISTORY = 30     # en dessous : « historique insuffisant » (SPEC §6.7)
PRIOR_RATE, PRIOR_WEIGHT, PRIOR_WEIGHT_DOW = 0.05, 20.0, 5.0
WINDOW_DAYS = 5

HISTORY_FEATURES = [
    "abs_p_7", "abs_p_30", "abs_p_90", "abs_l_90", "days_since_abs_p", "current_streak", "absent_at_ref",
    "hist_rate", "dow_rate",
    "late_7", "late_30", "late_min_mean_30", "rel_arrival_30", "arrival_slope_30",
    "hours_mean_7", "hours_mean_30", "hours_slope_30", "incomplete_30",
    "n_history", "seniority_years",
]
CALENDAR_FEATURES = [
    "dow_0", "dow_1", "dow_2", "dow_3", "dow_4", "month_sin", "month_cos",
    "veille_ferie", "lendemain_ferie", "pont", "days_to_holiday", "seance_unique", "ramadan", "gap_days",
]


# --------------------------------------------------------------------------- calendrier

class Calendar:
    def __init__(self, holidays: set[date], periods: list[tuple[str, date, date]]):
        self.holidays = set(holidays)
        self.periods = periods
        self._sorted = sorted(self.holidays)

    def is_working(self, d: date) -> bool:
        return d.weekday() < 5 and d not in self.holidays

    def next_working_days(self, ref: date, n: int) -> list[date]:
        out, d = [], ref
        while len(out) < n:
            d += timedelta(days=1)
            if self.is_working(d):
                out.append(d)
        return out

    def veille(self, d: date) -> bool:
        return (d + timedelta(days=1)) in self.holidays or (d.weekday() == 4 and (d + timedelta(days=3)) in self.holidays)

    def lendemain(self, d: date) -> bool:
        return (d - timedelta(days=1)) in self.holidays or (d.weekday() == 0 and (d - timedelta(days=3)) in self.holidays)

    def pont(self, d: date) -> bool:
        """Jour ouvré isolé entre un férié et un week-end (ex. vendredi après un jeudi férié)."""
        before, after = d - timedelta(days=1), d + timedelta(days=1)
        return (before in self.holidays and after.weekday() == 5) or (after in self.holidays and before.weekday() == 6)

    def days_to_holiday(self, d: date, cap: int = 60) -> int:
        for h in self._sorted:
            if h >= d:
                return min((h - d).days, cap)
        return cap

    def period(self, d: date) -> str | None:
        for kind, start, end in self.periods:
            if start <= d <= end:
                return kind
        return None

    def target_features(self, ref: date, targets: list[date]) -> dict[str, float]:
        """Features calendaires : jour cible (D1) ou fenêtre (W1, agrégats sur les jours)."""
        first = targets[0]
        f = {f"dow_{k}": float(first.weekday() == k) for k in range(5)}
        f["month_sin"] = math.sin(2 * math.pi * first.month / 12)
        f["month_cos"] = math.cos(2 * math.pi * first.month / 12)
        f["veille_ferie"] = float(sum(self.veille(t) for t in targets))
        f["lendemain_ferie"] = float(sum(self.lendemain(t) for t in targets))
        f["pont"] = float(sum(self.pont(t) for t in targets))
        f["days_to_holiday"] = float(self.days_to_holiday(first))
        kinds = [self.period(t) for t in targets]
        f["seance_unique"] = float(any(k == "SEANCE_UNIQUE" for k in kinds))
        f["ramadan"] = float(any(k == "RAMADAN" for k in kinds))
        f["gap_days"] = float((first - ref).days)
        return f


# --------------------------------------------------------------------------- panel individuel

@dataclass
class Panel:
    """Journées ouvrées d'un collaborateur, avec la classification finale des séries d'absence."""

    employee_id: int
    dates: np.ndarray            # datetime64[D], jours ouvrés triés
    absent: np.ndarray
    leave: np.ndarray
    late: np.ndarray
    late_minutes: np.ndarray
    present: np.ndarray
    first_in: np.ndarray
    ref_start: np.ndarray
    worked_h: np.ndarray         # heures des journées complètes, NaN sinon
    incomplete: np.ndarray
    run_len: np.ndarray          # longueur finale de la série d'absence contenant le jour (0 si présent)
    run_pos: np.ndarray          # position (1..n) du jour dans sa série
    hire_date: pd.Timestamp | None
    team: str | None

    @property
    def final_p(self) -> np.ndarray:
        return self.absent & (self.run_len < LONG_MIN_DAYS)

    @property
    def final_l(self) -> np.ndarray:
        return self.absent & (self.run_len >= LONG_MIN_DAYS)

    def index_of(self, d: date) -> int | None:
        i = int(np.searchsorted(self.dates, np.datetime64(d, "D"), side="right")) - 1
        return i if i >= 0 else None


def build_panels(ds: Dataset) -> dict[int, Panel]:
    emps = ds.employees.set_index("id")
    panels = {}
    for emp_id, g in ds.daily.sort_values("work_date").groupby("employee_id"):
        status = g["status"].to_numpy()
        absent_all = np.isin(status, [ABS_P, ABS_L])
        # séries calculées sur toutes les lignes (un samedi travaillé casse une série, comme côté Java)
        run_len_all = np.zeros(len(g), dtype=int)
        run_pos_all = np.zeros(len(g), dtype=int)
        start = None
        for k in range(len(g) + 1):
            if k < len(g) and absent_all[k]:
                if start is None:
                    start = k
                continue
            if start is not None:
                run_len_all[start:k] = k - start
                run_pos_all[start:k] = np.arange(1, k - start + 1)
                start = None
        wd = g["working_day"].to_numpy(dtype=bool)
        gw = g[wd]
        st = status[wd]
        worked = gw["worked_minutes"].to_numpy(dtype=float) / 60.0
        inc = gw["incomplete"].to_numpy(dtype=bool)
        present = np.isin(st, ["PRESENT", "RETARD"])
        e = emps.loc[emp_id] if emp_id in emps.index else None
        panels[int(emp_id)] = Panel(
            employee_id=int(emp_id),
            dates=gw["work_date"].to_numpy().astype("datetime64[D]"),
            absent=absent_all[wd], leave=st == CONGE, late=st == "RETARD",
            late_minutes=gw["late_minutes"].to_numpy(dtype=float), present=present,
            first_in=gw["first_in_min"].to_numpy(dtype=float), ref_start=gw["reference_start_min"].to_numpy(dtype=float),
            worked_h=np.where(present & ~inc, worked, np.nan), incomplete=inc & present,
            run_len=run_len_all[wd], run_pos=run_pos_all[wd],
            hire_date=e["hire_date"] if e is not None and pd.notna(e["hire_date"]) else None,
            team=str(e["team_name"]) if e is not None and pd.notna(e["team_name"]) else None,
        )
    return panels


# --------------------------------------------------------------------------- features d'historique

def _smoothed(k: float, n: float, weight: float) -> float:
    return (k + weight * PRIOR_RATE) / (n + weight)


def _slope(x: np.ndarray, y: np.ndarray) -> float:
    m = ~np.isnan(y)
    if m.sum() < 5:
        return 0.0
    x, y = x[m], y[m]
    xc = x - x.mean()
    den = (xc ** 2).sum()
    return float((xc * (y - y.mean())).sum() / den) if den > 0 else 0.0


def history_features(p: Panel, i: int, target_dows: list[int]) -> dict[str, float]:
    """Features au jour d'indice i (= date de référence R). N'utilise que p[: i + 1]."""
    R = p.dates[i]
    sl = slice(0, i + 1)
    absent, leave = p.absent[sl], p.leave[sl]
    # classification « telle que connue à R »
    as_of_p = absent & (p.run_len[sl] < LONG_MIN_DAYS)
    as_of_l = absent & (p.run_len[sl] >= LONG_MIN_DAYS)
    if absent[i]:
        pos = p.run_pos[i]
        run = slice(i - pos + 1, i + 1)
        ongoing_long = pos >= LONG_MIN_DAYS
        as_of_p = as_of_p.copy(); as_of_l = as_of_l.copy()
        as_of_p[run] = not ongoing_long
        as_of_l[run] = ongoing_long
    dates = p.dates[sl]

    def lo(days: int) -> int:
        return int(np.searchsorted(dates, R - np.timedelta64(days - 1, "D"), side="left"))

    l7, l30, l90 = lo(7), lo(30), lo(90)
    f: dict[str, float] = {
        "abs_p_7": float(as_of_p[l7:].sum()),
        "abs_p_30": float(as_of_p[l30:].sum()),
        "abs_p_90": float(as_of_p[l90:].sum()),
        "abs_l_90": float(as_of_l[l90:].sum()),
    }
    idx_p = np.flatnonzero(as_of_p)
    f["days_since_abs_p"] = float(min(i - idx_p[-1], 250)) if len(idx_p) else 250.0
    f["current_streak"] = float(p.run_pos[i]) if absent[i] else 0.0
    f["absent_at_ref"] = float(absent[i])
    eligible = ~(as_of_l | leave)
    f["hist_rate"] = _smoothed(as_of_p.sum(), eligible.sum(), PRIOR_WEIGHT)
    dows = (dates.astype("datetime64[D]").view("int64") - 4) % 7  # 0 = lundi
    rates = [_smoothed(as_of_p[dows == w].sum(), eligible[dows == w].sum(), PRIOR_WEIGHT_DOW) for w in target_dows]
    f["dow_rate"] = float(np.mean(rates))

    late, pres = p.late[sl], p.present[sl]
    f["late_7"] = float(late[l7:].sum())
    f["late_30"] = float(late[l30:].sum())
    lm = p.late_minutes[sl][l30:][pres[l30:]]
    f["late_min_mean_30"] = float(lm.mean()) if len(lm) else 0.0
    rel = (p.first_in[sl] - p.ref_start[sl])[l30:][pres[l30:]]
    rel = rel[~np.isnan(rel)]
    f["rel_arrival_30"] = float(np.clip(rel, -120, 240).mean()) if len(rel) else 0.0
    xday = (dates[l30:] - R).astype(float)
    f["arrival_slope_30"] = _slope(xday, np.where(pres[l30:], p.first_in[sl][l30:], np.nan))
    wh = p.worked_h[sl]
    f["hours_mean_7"] = float(np.nanmean(wh[l7:])) if np.any(~np.isnan(wh[l7:])) else np.nan
    f["hours_mean_30"] = float(np.nanmean(wh[l30:])) if np.any(~np.isnan(wh[l30:])) else np.nan
    f["hours_slope_30"] = _slope(xday, wh[l30:])
    f["incomplete_30"] = float(p.incomplete[sl][l30:].sum())
    f["n_history"] = float(i + 1)
    if p.hire_date is not None:
        f["seniority_years"] = float((pd.Timestamp(R) - p.hire_date).days / 365.25)
    else:
        f["seniority_years"] = np.nan
    return f


# --------------------------------------------------------------------------- échantillons

def sample_row(p: Panel, i: int, cal: Calendar, horizon: str) -> dict | None:
    R = pd.Timestamp(p.dates[i]).date()
    targets = cal.next_working_days(R, 1 if horizon == "D1" else WINDOW_DAYS)
    row = {"employee_id": p.employee_id, "ref_date": R, "target_date": targets[0], "target_end": targets[-1]}
    row.update(history_features(p, i, [t.weekday() for t in targets]))
    row.update(cal.target_features(R, targets))
    row["team"] = p.team
    return row


def label(p: Panel, targets: list[date]) -> tuple[float | None, int, int]:
    """(y, nb jours d'absence ponctuelle, nb jours éligibles) ; y=None si l'échantillon est exclu."""
    idx = [p.index_of(t) for t in targets]
    if any(k is None or pd.Timestamp(p.dates[k]).date() != t for k, t in zip(idx, targets)):
        return None, 0, 0  # hors période suivie
    final_p, final_l = p.final_p, p.final_l
    elig = [k for k in idx if not (final_l[k] or p.leave[k])]
    min_elig = 1 if len(targets) == 1 else 3
    if len(elig) < min_elig:
        return None, 0, len(elig)
    n_abs = int(sum(final_p[k] for k in elig))
    return float(n_abs > 0), n_abs, len(elig)


def build_samples(ds: Dataset, horizon: str, min_history: int = MIN_TRAIN_HISTORY) -> pd.DataFrame:
    cal = Calendar(ds.holidays, ds.periods)
    rows = []
    for p in build_panels(ds).values():
        for i in range(min_history - 1, len(p.dates)):
            row = sample_row(p, i, cal, horizon)
            targets = cal.next_working_days(row["ref_date"], 1 if horizon == "D1" else WINDOW_DAYS)
            y, n_abs, n_elig = label(p, targets)
            if y is None:
                continue
            row.update(y=y, n_abs=n_abs, n_eligible=n_elig)
            rows.append(row)
    df = pd.DataFrame(rows)
    if not df.empty:
        for c in ("ref_date", "target_date", "target_end"):
            df[c] = pd.to_datetime(df[c])
        df = df.sort_values(["ref_date", "employee_id"]).reset_index(drop=True)
    return df


def inference_rows(ds: Dataset, employee_ids: list[int], ref: date, horizon: str) -> list[dict]:
    """Une ligne par collaborateur à la date de référence `ref` (dernier jour ouvré connu ≤ ref)."""
    cal = Calendar(ds.holidays, ds.periods)
    panels = build_panels(ds)
    out = []
    for emp in employee_ids:
        p = panels.get(int(emp))
        i = p.index_of(ref) if p is not None else None
        if p is None or i is None:
            out.append({"employee_id": emp, "status": "INSUFFICIENT_HISTORY", "n_history": 0})
            continue
        row = sample_row(p, i, cal, horizon)
        # la cible part de `ref` (et non du dernier jour ouvré du collaborateur)
        targets = cal.next_working_days(ref, 1 if horizon == "D1" else WINDOW_DAYS)
        row.update(cal.target_features(ref, targets))
        row.update(target_date=targets[0], target_end=targets[-1], ref_date=ref)
        row["status"] = "OK" if row["n_history"] >= MIN_DISPLAY_HISTORY else "INSUFFICIENT_HISTORY"
        out.append(row)
    return out


def feature_columns(teams: list[str]) -> list[str]:
    return HISTORY_FEATURES + CALENDAR_FEATURES + [f"team__{t}" for t in teams]


def to_matrix(df: pd.DataFrame, teams: list[str], fill: dict[str, float] | None = None) -> pd.DataFrame:
    X = df.reindex(columns=HISTORY_FEATURES + CALENDAR_FEATURES).astype(float).copy()
    for t in teams:
        X[f"team__{t}"] = (df["team"] == t).astype(float).to_numpy() if "team" in df else 0.0
    if fill:
        X = X.fillna(fill)
    return X
