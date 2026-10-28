from fastapi.testclient import TestClient

from app.main import app


def test_health_is_always_answered():
    r = TestClient(app).get("/health")
    assert r.status_code == 200
    assert r.json()["status"] in ("UP", "DEGRADED")
