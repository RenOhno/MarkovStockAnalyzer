import importlib
import json

from fastapi.testclient import TestClient
import pytest


TOKEN = "security-test-token"


@pytest.fixture
def client(monkeypatch):
    monkeypatch.setenv("INTERNAL_API_TOKEN", TOKEN)
    from app.main import create_app

    return TestClient(create_app())


def test_correct_token_allows_health(client):
    response = client.get(
        "/internal/v1/health",
        headers={"X-Internal-Token": TOKEN},
    )

    assert response.status_code == 200


def test_missing_token_returns_unified_error(client):
    response = client.get("/internal/v1/health")

    assert response.status_code == 401
    assert response.json()["code"] == "INTERNAL_AUTH_FAILED"
    assert set(response.json()) == {"code", "message", "requestId", "details"}


def test_wrong_token_returns_unified_error_without_token_value(client):
    wrong_token = "wrong-secret-token"
    response = client.get(
        "/internal/v1/health",
        headers={"X-Internal-Token": wrong_token},
    )

    assert response.status_code == 401
    assert wrong_token not in response.text
    assert TOKEN not in response.text
    assert response.json()["code"] == "INTERNAL_AUTH_FAILED"


def test_request_id_is_preserved_on_success(client):
    request_id = "request-123.example"
    response = client.get(
        "/internal/v1/health",
        headers={
            "X-Internal-Token": TOKEN,
            "X-Request-Id": request_id,
        },
    )

    assert response.status_code == 200
    assert response.headers["X-Request-Id"] == request_id


def test_request_id_is_generated_when_missing(client):
    response = client.get(
        "/internal/v1/health",
        headers={"X-Internal-Token": TOKEN},
    )

    request_id = response.headers["X-Request-Id"]
    assert len(request_id) == 36
    assert request_id.count("-") == 4


def test_error_request_id_matches_response_header(client):
    request_id = "error-request-456"
    response = client.get(
        "/internal/v1/health",
        headers={"X-Request-Id": request_id},
    )

    assert response.status_code == 401
    assert response.headers["X-Request-Id"] == request_id
    assert response.json()["requestId"] == request_id


def test_invalid_request_id_is_replaced(client):
    invalid_request_id = "x" * 129
    response = client.get(
        "/internal/v1/health",
        headers={
            "X-Internal-Token": TOKEN,
            "X-Request-Id": invalid_request_id,
        },
    )

    assert response.status_code == 200
    assert response.headers["X-Request-Id"] != invalid_request_id
    assert len(response.headers["X-Request-Id"]) == 36


def test_not_found_uses_unified_error(client):
    response = client.get(
        "/internal/v1/does-not-exist",
        headers={"X-Internal-Token": TOKEN},
    )

    assert response.status_code == 404
    assert set(response.json()) == {"code", "message", "requestId", "details"}
    assert response.json()["code"] == "NOT_FOUND"
    assert response.headers["X-Request-Id"] == response.json()["requestId"]


def test_missing_token_configuration_fails_safely(monkeypatch):
    monkeypatch.delenv("INTERNAL_API_TOKEN", raising=False)
    from app.main import create_app

    with pytest.raises(
        RuntimeError,
        match="INTERNAL_API_TOKEN_NOT_CONFIGURED",
    ):
        create_app()


def test_openapi_does_not_contain_token_value(client):
    response = client.get("/openapi.json")

    assert response.status_code == 200
    assert TOKEN not in json.dumps(response.json())


def test_app_import_and_health_do_not_call_yfinance(monkeypatch):
    calls = []
    monkeypatch.setenv("INTERNAL_API_TOKEN", TOKEN)
    monkeypatch.setattr(
        "yfinance.download",
        lambda *args, **kwargs: calls.append("yfinance"),
    )

    main_module = importlib.import_module("app.main")
    response = TestClient(main_module.create_app()).get(
        "/internal/v1/health",
        headers={"X-Internal-Token": TOKEN},
    )

    assert response.status_code == 200
    assert calls == []
