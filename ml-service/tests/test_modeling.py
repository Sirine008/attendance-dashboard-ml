import numpy as np
import pandas as pd
import pytest

from pipeline import explain
from pipeline.features import build_samples
from pipeline.modeling import baseline_model, fit_candidate
from pipeline.train import metrics, precision_at_k


@pytest.fixture(scope="module")
def d1(small_ds):
    return build_samples(small_ds, "D1")


@pytest.mark.parametrize("kind", ["logistic", "lgbm"])
def test_candidates_produce_calibrated_probabilities_and_three_factors(d1, kind):
    teams = sorted(d1.team.dropna().unique())
    train, test = d1[d1.target_date < "2025-01-01"], d1[d1.target_date >= "2025-01-01"].head(20)
    m = fit_candidate(kind, train, teams, "D1")
    m.thresholds = {"MODERE": 0.05, "ELEVE": 0.1}
    p = m.predict_proba(test)
    assert ((p >= 0) & (p <= 1)).all()
    assert abs(p.mean() - train.y.mean()) < 0.1  # calibration : pas de probabilités gonflées par class_weight
    factors = m.explain(test)
    assert all(1 <= len(f) <= 3 for f in factors)
    assert all(x["direction"] in ("UP", "DOWN") and x["label"] for f in factors for x in f)
    assert m.risk_level(0.2) == "ELEVE" and m.risk_level(0.07) == "MODERE" and m.risk_level(0.01) == "FAIBLE"


def test_baseline_explains_with_history(d1):
    m = baseline_model("baseline_dow", d1, [], "D1")
    f = m.explain(d1.head(5))
    assert f[0][0]["feature"] == "dow_rate"
    assert "taux historique d'absence le" in f[0][0]["label"]


def test_factor_wording_never_claims_causality(d1):
    teams = sorted(d1.team.dropna().unique())
    m = fit_candidate("logistic", d1, teams, "D1")
    for factors in m.explain(d1.sample(50, random_state=0)):
        for f in factors:
            assert "cause" not in f["label"].lower()


def test_precision_at_k_and_metrics():
    df = pd.DataFrame({"target_date": ["d1"] * 4 + ["d2"] * 4, "y": [1, 0, 0, 0, 0, 0, 1, 1]})
    proba = np.array([0.9, 0.8, 0.1, 0.0, 0.5, 0.4, 0.3, 0.2])
    assert precision_at_k(df, proba, 3) == pytest.approx((1 / 3 + 1 / 3) / 2)
    m = metrics(df, proba, {"MODERE": 0.3, "ELEVE": 0.6})
    assert m["positives"] == 3 and 0 <= m["pr_auc"] <= 1 and m["recall_eleve"] == pytest.approx(1 / 3, abs=1e-3)


def test_phrase_examples():
    row = pd.Series({"abs_p_30": 3.0, "veille_ferie": 1.0, "dow_0": 1.0, "dow_rate": 0.12})
    assert explain.phrase("abs_p_30", row, "D1") == "3 absences ponctuelles sur les 30 derniers jours"
    assert explain.phrase("veille_ferie", row, "D1") == "veille de jour férié"
    assert explain.phrase("dow_rate", row, "D1") == "taux historique d'absence le lundi : 12%"
