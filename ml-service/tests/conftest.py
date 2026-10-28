import pandas as pd
import pytest

from pipeline.calendar_tn import PUBLIC_HOLIDAYS, SPECIAL_PERIODS
from pipeline.data import Dataset, normalize
from synthetic.generator import generate


@pytest.fixture(scope="session")
def small_ds() -> Dataset:
    """Petit jeu synthétique (aucune base requise), avec quelques congés déclarés ajoutés."""
    emps, daily, _ = generate(n=6, seed=7)
    daily = daily.copy()
    # 3 jours de congé déclaré pour le 1er collaborateur
    mask = (daily.employee_id == -1) & daily.work_date.between(pd.Timestamp("2024-09-02"), pd.Timestamp("2024-09-04"))
    daily.loc[mask, ["status", "first_in", "late_minutes", "worked_minutes"]] = ["CONGE", None, 0, None]
    return normalize(Dataset(emps, daily, set(PUBLIC_HOLIDAYS), SPECIAL_PERIODS))


def pytest_configure(config):
    import os
    os.environ.setdefault("ML_REGISTER_ON_STARTUP", "false")
