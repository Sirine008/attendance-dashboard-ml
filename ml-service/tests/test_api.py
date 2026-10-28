"""Tests de l'API FastAPI (aucune base requise : données et modèle injectés)."""
import json

import joblib
import pytest
from fastapi.testclient import TestClient

from app import main
from app.model_store import ModelStore
from pipeline.features import build_samples
from pipeline.modeling import baseline_model, fit_candidate


@pytest.fixture(scope="module")
def client(small_ds, tmp_path_factory):
    model_dir = tmp_path_factory.mktemp("models")
    d1, w1 = build_samples(small_ds, "D1"), build_samples(small_ds, "W1")
    teams = sorted(d1.team.dropna().unique())
    m1 = fit_candidate("lgbm", d1, teams, "D1")
    m1.thresholds = {"MODERE": 0.05, "ELEVE": 0.12}
    m2 = baseline_model("baseline_dow", w1, teams, "W1")
    m2.thresholds = {"MODERE": 0.25, "ELEVE": 0.4}
    summary = {"version": "vtest", "trained_at": "2025-05-20T00:00:00", "data": {"employees": 6},
               "horizons": {"D1": {"algorithm": "lgbm"}, "W1": {"algorithm": "baseline_dow"}}}
    joblib.dump({"version": "vtest", "summary": summary, "models": {"D1": m1, "W1": m2}}, model_dir / "vtest.joblib")
    (model_dir / "ACTIVE").write_text("vtest")

    services = main.Services()
    services.store = ModelStore(model_dir)
    services.store.load_active()
    services.load_data = lambda: small_ds
    services.train = lambda: summary
    main.app.dependency_overrides[main.get_services] = lambda: services
    yield TestClient(main.app)
    main.app.dependency_overrides.clear()


def test_health_reports_loaded_model(client):
    r = client.get("/health").json()
    assert r == {"status": "UP", "model_loaded": True, "model_version": "vtest"}


def test_predict_batch(client, small_ds):
    ids = [int(i) for i in small_ds.employees.id] + [12345]
    r = client.post("/predict", json={"reference_date": "2025-05-16", "employee_ids": ids})
    assert r.status_code == 200
    body = r.json()
    assert body["model_version"] == "vtest"
    preds = body["predictions"]
    assert len(preds) == 2 * len(ids)
    unknown = [p for p in preds if p["employee_id"] == 12345]
    assert all(p["status"] == "INSUFFICIENT_HISTORY" and p["probability"] is None for p in unknown)
    ok = [p for p in preds if p["status"] == "OK"]
    assert ok and all(0 <= p["probability"] <= 1 and p["risk_level"] in ("FAIBLE", "MODERE", "ELEVE") for p in ok)
    assert all(1 <= len(p["factors"]) <= 3 for p in ok)
    d1 = next(p for p in ok if p["horizon"] == "D1")
    assert d1["target_date"] == "2025-05-19"  # vendredi → lundi
    w1 = next(p for p in ok if p["horizon"] == "W1")
    assert w1["target_end"] == "2025-05-23" and w1["expected_days"] is not None


def test_predict_validation(client):
    assert client.post("/predict", json={"reference_date": "2025-05-16", "employee_ids": []}).status_code == 422
    assert client.post("/predict", json={"reference_date": "2025-05-16", "employee_ids": [1], "horizons": ["M1"]}).status_code == 422


def test_train_requires_admin_token(client):
    assert client.post("/train").status_code == 403
    r = client.post("/train", headers={"X-Admin-Token": main.ADMIN_TOKEN})
    assert r.status_code == 200 and r.json()["version"] == "vtest"


def test_model_info_and_backtest(client):
    info = client.get("/model/info").json()
    assert info["version"] == "vtest" and set(info["horizons"]) == {"D1", "W1"}
    r = client.get("/model/backtest", params={"weeks": 8, "synthetic": "true"})
    assert r.status_code == 200, r.text
    bt = r.json()
    assert 7 <= len(bt["weeks"]) <= 9
    assert all(w["samples"] > 0 for w in bt["weeks"])
    assert bt["note"]
    assert client.get("/model/backtest").status_code == 404  # aucun collaborateur réel dans ce jeu
