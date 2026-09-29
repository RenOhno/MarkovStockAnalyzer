from fastapi.testclient import TestClient

from app.main import app


client = TestClient(app)


def test_health_returns_up_and_engine_version():
    response = client.get("/internal/v1/health")

    assert response.status_code == 200
    assert response.json() == {
        "status": "UP",
        "engineVersion": "msa-core-v1",
    }


def test_unknown_url_returns_404():
    response = client.get("/internal/v1/unknown")

    assert response.status_code == 404


def test_health_is_registered_in_openapi_as_get():
    response = client.get("/openapi.json")

    assert response.status_code == 200
    paths = response.json()["paths"]
    assert "/internal/v1/health" in paths
    assert "get" in paths["/internal/v1/health"]


def test_import_and_health_do_not_call_network_or_analysis(monkeypatch):
    calls = []

    monkeypatch.setattr(
        "yfinance.download",
        lambda *args, **kwargs: calls.append("yfinance"),
    )
    monkeypatch.setattr(
        "app.services.analysis_service.AnalysisService.analyze",
        lambda *args, **kwargs: calls.append("analysis"),
    )

    response = client.get("/internal/v1/health")

    assert response.status_code == 200
    assert calls == []
