"""Tests de non-fuite temporelle et des cibles (SPEC §8 : aucune feature n'utilise une date ≥ date cible)."""
from datetime import date

import numpy as np
import pandas as pd
import pytest

from pipeline import features as F
from pipeline.calendar_tn import PUBLIC_HOLIDAYS, SPECIAL_PERIODS
from pipeline.data import Dataset


def _truncate(ds: Dataset, ref: pd.Timestamp) -> Dataset:
    return Dataset(ds.employees, ds.daily[ds.daily.work_date <= ref], ds.holidays, ds.periods)


def _features_at(ds: Dataset, emp: int, ref: pd.Timestamp, horizon: str) -> dict:
    p = F.build_panels(ds)[emp]
    i = p.index_of(ref.date())
    cal = F.Calendar(ds.holidays, ds.periods)
    row = F.sample_row(p, i, cal, horizon)
    return {k: row[k] for k in F.HISTORY_FEATURES + F.CALENDAR_FEATURES}


def _assert_same(a: dict, b: dict):
    for k in a:
        if isinstance(a[k], float) and np.isnan(a[k]):
            assert np.isnan(b[k]), k
        else:
            assert a[k] == pytest.approx(b[k]), k


@pytest.mark.parametrize("horizon", ["D1", "W1"])
def test_features_only_use_data_up_to_reference_date(small_ds, horizon):
    """Les features à R sont identiques que l'on dispose de tout l'historique ou seulement des jours ≤ R."""
    rng = np.random.default_rng(0)
    samples = F.build_samples(small_ds, horizon)
    for _, s in samples.sample(60, random_state=1).iterrows():
        full = _features_at(small_ds, s.employee_id, s.ref_date, horizon)
        trunc = _features_at(_truncate(small_ds, s.ref_date), s.employee_id, s.ref_date, horizon)
        _assert_same(full, trunc)
        assert s.target_date > s.ref_date


def test_future_perturbation_does_not_change_features(small_ds):
    """Inverser au hasard les statuts des jours ≥ date cible ne change aucune feature."""
    samples = F.build_samples(small_ds, "D1")
    rng = np.random.default_rng(3)
    for _, s in samples.sample(30, random_state=2).iterrows():
        d = small_ds.daily.copy()
        future = (d.employee_id == s.employee_id) & (d.work_date >= s.target_date)
        flip = future & (rng.random(len(d)) < 0.5)
        d.loc[flip, "status"] = np.where(d.loc[flip, "status"] == "PRESENT", "ABSENCE_PONCTUELLE", "PRESENT")
        perturbed = Dataset(small_ds.employees, d, small_ds.holidays, small_ds.periods)
        _assert_same(_features_at(small_ds, s.employee_id, s.ref_date, "D1"),
                     _features_at(perturbed, s.employee_id, s.ref_date, "D1"))


def test_ongoing_absence_run_is_classified_with_known_length_only(small_ds):
    """Au 2e jour d'une série qui deviendra longue, la série est encore comptée comme ponctuelle."""
    for p in F.build_panels(small_ds).values():
        long_runs = np.flatnonzero(p.final_l & (p.run_pos == 2))
        if len(long_runs):
            i = int(long_runs[0])
            f = F.history_features(p, i, [0])
            assert f["current_streak"] == 2
            assert f["abs_p_7"] >= 2  # les 2 jours connus sont ponctuels à R
            return
    pytest.skip("aucune série longue dans le jeu de test")


def test_targets_exclude_leave_and_long_absence(small_ds):
    samples = F.build_samples(small_ds, "D1")
    d = small_ds.daily.set_index(["employee_id", "work_date"])["status"]
    statuses = [d.loc[(r.employee_id, r.target_date)] for r in samples.itertuples()]
    assert "CONGE" not in statuses and "ABSENCE_LONGUE" not in statuses
    assert all((st == "ABSENCE_PONCTUELLE") == bool(y) for st, y in zip(statuses, samples.y))


def test_week_target_counts_only_eligible_days(small_ds):
    w = F.build_samples(small_ds, "W1")
    assert (w.n_eligible >= 3).all() and (w.n_eligible <= 5).all()
    assert ((w.n_abs > 0) == (w.y == 1)).all()


def test_calendar_features():
    cal = F.Calendar(set(PUBLIC_HOLIDAYS), SPECIAL_PERIODS)
    assert cal.veille(date(2024, 4, 8))          # lundi avant les Martyrs (mardi 09/04)
    assert cal.lendemain(date(2024, 4, 12))      # vendredi après l'Aïd (10–11/04)
    assert cal.pont(date(2024, 4, 12))           # vendredi coincé entre férié et week-end
    assert cal.veille(date(2024, 7, 19)) is False
    assert cal.next_working_days(date(2024, 4, 5), 2) == [date(2024, 4, 8), date(2024, 4, 12)]
    assert cal.period(date(2024, 3, 15)) == "RAMADAN" and cal.period(date(2024, 7, 15)) == "SEANCE_UNIQUE"


def test_inference_marks_short_history_as_insufficient(small_ds):
    emp = int(small_ds.employees.id.iloc[0])
    first = small_ds.daily[small_ds.daily.employee_id == emp].work_date.min()
    rows = F.inference_rows(small_ds, [emp], (first + pd.Timedelta(days=10)).date(), "D1")
    assert rows[0]["status"] == "INSUFFICIENT_HISTORY"
    rows = F.inference_rows(small_ds, [emp, 999], date(2025, 5, 16), "D1")
    assert rows[0]["status"] == "OK" and rows[0]["target_date"] == date(2025, 5, 19)
    assert rows[1]["status"] == "INSUFFICIENT_HISTORY"
