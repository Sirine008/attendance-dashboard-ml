"""Générateur de données synthétiques (démo à plus grande échelle, ~60 collaborateurs).

Calibré sur les statistiques des données réelles (horaires, absences par jour de semaine, effet veille /
lendemain de férié, absences longues, heures travaillées, anomalies de pointage). Les collaborateurs générés
ont un code `SYNxxx` : le backend les marque `is_synthetic = true` à l'import et l'UI les signale.

Usage : python -m synthetic.generator [--n 60] [--seed 42] [--out ../data/synthetic/Pointage_synthetique.xlsx]
"""
from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from datetime import date, datetime, time, timedelta
from pathlib import Path

import numpy as np
import pandas as pd

from pipeline.calendar_tn import PUBLIC_HOLIDAYS, SPECIAL_PERIODS
from pipeline.features import Calendar

ROOT = Path(__file__).resolve().parents[2]
START, END = date(2024, 1, 2), date(2025, 5, 19)

# Valeurs par défaut = statistiques mesurées sur les données réelles (docs/data_profile.md) ;
# remplacées par `calibrate()` quand la base est disponible.
DEFAULT_PARAMS = {
    "short_rate_by_dow": [0.090, 0.034, 0.027, 0.033, 0.082],
    "veille_multiplier": 3.1,
    "lendemain_multiplier": 4.2,
    "second_day_prob": 0.30,
    "long_runs_per_year": 2.0,
    "long_run_lengths": [3, 3, 3, 3, 3, 3, 3, 4, 4, 5, 5, 6, 7, 9, 9, 9, 36],
    "reference_starts_min": [450, 465, 480, 480, 480, 495, 510, 525, 540, 585],
    "late_tolerance": 10,
    "worked_hours_mean": 7.3,
    "worked_hours_sd": 0.8,
    "incomplete_rate": 0.027,
    "duplicate_rate": 0.007,
    "internal_move_prob": 0.25,
}


def calibrate(ds) -> dict:
    """Mesure les paramètres sur les données réelles (`pipeline.data.Dataset`, collaborateurs non synthétiques)."""
    p = dict(DEFAULT_PARAMS)
    d = ds.daily[ds.daily.working_day].copy()
    d["dow"] = d.work_date.dt.weekday
    p["short_rate_by_dow"] = [round(float((g.status == "ABSENCE_PONCTUELLE").mean()), 4) for _, g in d.groupby("dow")]
    cal = Calendar(ds.holidays, ds.periods)
    base = (d.status == "ABSENCE_PONCTUELLE").mean()
    vei = d[[cal.veille(x.date()) for x in d.work_date]]
    len_ = d[[cal.lendemain(x.date()) for x in d.work_date]]
    if len(vei) and base > 0:
        p["veille_multiplier"] = round(float((vei.status == "ABSENCE_PONCTUELLE").mean() / base), 2)
    if len(len_) and base > 0:
        p["lendemain_multiplier"] = round(float((len_.status == "ABSENCE_PONCTUELLE").mean() / base), 2)
    runs = []
    for _, g in d.sort_values("work_date").groupby("employee_id"):
        absent = g.status.isin(["ABSENCE_PONCTUELLE", "ABSENCE_LONGUE"]).to_numpy()
        n = 0
        for a in list(absent) + [False]:
            if a:
                n += 1
            elif n:
                runs.append(n)
                n = 0
    runs = np.array(runs)
    if len(runs):
        short = runs[runs < 3]
        p["second_day_prob"] = round(float((short == 2).mean()), 3) if len(short) else p["second_day_prob"]
        person_years = d.groupby("employee_id").size().sum() / 250
        p["long_run_lengths"] = sorted(int(x) for x in runs[runs >= 3])
        p["long_runs_per_year"] = round(float((runs >= 3).sum() / person_years), 2)
    refs = d.dropna(subset=["reference_start_min"]).groupby("employee_id").reference_start_min.first()
    if len(refs):
        p["reference_starts_min"] = sorted(float(x) for x in refs)
    pres = d[d.status.isin(["PRESENT", "RETARD"])]
    complete = pres[~pres.incomplete & pres.worked_minutes.notna()]
    p["worked_hours_mean"] = round(float(complete.worked_minutes.mean() / 60), 2)
    p["worked_hours_sd"] = round(float(complete.groupby("employee_id").worked_minutes.mean().std() / 60), 2)
    p["incomplete_rate"] = round(float(pres.incomplete.mean()), 4)
    return p


@dataclass
class Person:
    code: str
    ref_start: float
    rate_mult: float
    arrival_mu: float
    arrival_sd: float
    hours: float
    lunch_prob: float
    start: date
    hire: date


def _people(n: int, rng: np.random.Generator, params: dict) -> list[Person]:
    out = []
    for k in range(1, n + 1):
        late_hire = rng.random() < 0.12
        start = START + timedelta(days=int(rng.integers(120, 300))) if late_hire else START
        hire = start if late_hire else date(int(rng.integers(2008, 2023)), int(rng.integers(1, 13)), 1)
        ref = float(rng.choice(params["reference_starts_min"])) + float(rng.choice([-15, 0, 0, 15]))
        out.append(Person(
            code=f"SYN{k:03d}", ref_start=ref, rate_mult=float(rng.gamma(2.0, 0.5)),
            arrival_mu=float(rng.normal(0, 9)), arrival_sd=float(rng.uniform(6, 22)),
            hours=float(np.clip(rng.normal(params["worked_hours_mean"], params["worked_hours_sd"]), 5.0, 9.5)),
            lunch_prob=float(rng.uniform(0.4, 0.95)), start=start, hire=hire))
    return out


def _hm(minutes: float) -> time:
    m = int(np.clip(minutes, 0, 23 * 60 + 59.98) * 60)
    return time(m // 3600, (m % 3600) // 60, m % 60)


def generate(n: int = 60, seed: int = 42, params: dict | None = None):
    """Retourne (employees_df, daily_df, punches_df) au même format que la base."""
    params = params or DEFAULT_PARAMS
    rng = np.random.default_rng(seed)
    cal = Calendar(set(PUBLIC_HOLIDAYS), SPECIAL_PERIODS)
    days = [START + timedelta(days=i) for i in range((END - START).days + 1)]
    working = [d for d in days if cal.is_working(d)]
    people = _people(n, rng, params)
    emp_rows, daily_rows, punch_rows = [], [], []
    tol = params["late_tolerance"]
    for idx, p in enumerate(people, start=1):
        emp_id = -idx  # identifiants négatifs : jamais confondus avec la base
        emp_rows.append({"id": emp_id, "code": p.code, "team_id": -1, "team_name": "Équipe démo (données synthétiques)",
                         "hire_date": pd.Timestamp(p.hire), "is_synthetic": True})
        my_days = [d for d in working if d >= p.start]
        absent = np.zeros(len(my_days), dtype=bool)
        # absences longues (processus de Poisson)
        i = 0
        lam = params["long_runs_per_year"] / 250
        while i < len(my_days):
            if rng.random() < lam:
                L = int(rng.choice(params["long_run_lengths"]))
                absent[i:i + L] = True
                i += L + 1
            i += 1
        # absences ponctuelles
        i = 0
        while i < len(my_days):
            d = my_days[i]
            if absent[i]:
                i += 1
                continue
            pr = params["short_rate_by_dow"][d.weekday()] * p.rate_mult
            if cal.veille(d):
                pr *= params["veille_multiplier"]
            if cal.lendemain(d):
                pr *= params["lendemain_multiplier"]
            if rng.random() < min(pr, 0.6):
                absent[i] = True
                if rng.random() < params["second_day_prob"] and i + 1 < len(my_days):
                    absent[i + 1] = True
                    i += 1
                i += 2  # au moins un jour de présence après une absence ponctuelle
                continue
            i += 1
        # statut final : séries ≥ 3 jours = absence longue
        status = np.where(absent, "ABSENCE_PONCTUELLE", "PRESENT").astype(object)
        k = 0
        while k < len(my_days):
            if absent[k]:
                j = k
                while j < len(my_days) and absent[j]:
                    j += 1
                if j - k >= 3:
                    status[k:j] = "ABSENCE_LONGUE"
                k = j
            else:
                k += 1
        for d, st in zip(my_days, status):
            row = {"employee_id": emp_id, "work_date": pd.Timestamp(d), "working_day": True, "status": st,
                   "first_in": None, "late_minutes": 0, "worked_minutes": None, "incomplete": False,
                   "reference_start": _hm(p.ref_start)}
            if st == "PRESENT":
                delay = rng.normal(p.arrival_mu, p.arrival_sd)
                if rng.random() < 0.05:
                    delay += rng.uniform(30, 90)
                arrival = p.ref_start + delay
                worked = p.hours * 60 + rng.normal(0, 25)
                punches = [(arrival, "IN", "TB")]
                if rng.random() < p.lunch_prob:
                    out_l = 12 * 60 + 30 + rng.normal(0, 20)
                    back = out_l + rng.uniform(40, 80)
                    punches += [(out_l, "OUT", "TB"), (back, "IN", "TB")]
                    leave_t = arrival + worked + (back - out_l)
                else:
                    leave_t = arrival + worked
                punches.append((leave_t, "OUT", "TB"))
                if rng.random() < params["internal_move_prob"]:
                    t0 = arrival + rng.uniform(60, 180)
                    punches += [(t0, "OUT", "SS1 B"), (t0 + rng.uniform(5, 40), "IN", "SS1 B")]
                punches.sort()
                incomplete = False
                if rng.random() < params["incomplete_rate"]:
                    boundary = [q for q in punches if q[2] == "TB" and q is not punches[0]]
                    if boundary:
                        punches.remove(boundary[int(rng.integers(len(boundary)))])
                        incomplete = True
                if rng.random() < params["duplicate_rate"]:
                    q = punches[int(rng.integers(len(punches)))]
                    punches.append((q[0] + 3 / 60, q[1], q[2]))
                    punches.sort()
                first_in = arrival
                late = first_in > p.ref_start + tol
                row.update(first_in=_hm(first_in), late_minutes=int(first_in - p.ref_start) if late else 0,
                           status="RETARD" if late else "PRESENT", incomplete=incomplete,
                           worked_minutes=None if incomplete else int(worked))
                for t, direction, door in punches:
                    punch_rows.append({"code": p.code, "ts": datetime.combine(d, _hm(t)), "direction": direction, "door": door})
            daily_rows.append(row)
    return pd.DataFrame(emp_rows), pd.DataFrame(daily_rows), pd.DataFrame(punch_rows)


def write_xlsx(punches: pd.DataFrame, path: Path) -> None:
    """Même format que le fichier réel : ligne 1 vide, en-tête ligne 2, une feuille par collaborateur."""
    from openpyxl import Workbook
    path.parent.mkdir(parents=True, exist_ok=True)
    wb = Workbook(write_only=True)
    for code, g in punches.sort_values(["code", "ts"]).groupby("code"):
        ws = wb.create_sheet(code)
        ws.append([])
        ws.append(["", "_Time", "User_ID", "User_Name", "Terminal_Name"])
        name = f"COLLABORATEUR {code} (SYNTHETIQUE)"
        for r in g.itertuples():
            verb = "Entrée" if r.direction == "IN" else "Sortie "
            ws.append(["", r.ts.strftime("%d/%m/%Y %H:%M:%S"), code, name, f"{verb} {r.door}"])
    wb.save(path)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--n", type=int, default=60)
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--out", default=str(ROOT / "data" / "synthetic" / "Pointage_synthetique.xlsx"))
    ap.add_argument("--calibrate-from-db", action="store_true")
    args = ap.parse_args()
    params = DEFAULT_PARAMS
    if args.calibrate_from_db:
        from pipeline import data
        params = calibrate(data.load(data.engine()).subset(synthetic=False))
        out_json = ROOT / "ml-service" / "reports" / "synthetic_calibration.json"
        out_json.write_text(json.dumps(params, indent=2), encoding="utf-8")
    emps, daily, punches = generate(args.n, args.seed, params)
    write_xlsx(punches, Path(args.out))
    print(f"{len(emps)} collaborateurs synthétiques, {len(daily)} journées, {len(punches)} pointages -> {args.out}")


if __name__ == "__main__":
    main()
