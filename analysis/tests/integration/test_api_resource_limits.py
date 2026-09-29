from datetime import date

from fastapi.testclient import TestClient

from app.api.limits import MAX_JSON_BYTES
from app.providers.base import MarketDataProvider


TOKEN = "resource-limit-test-token"
HEADERS = {"X-Internal-Token": TOKEN}


class NoNetworkProvider(MarketDataProvider):
    def fetch(self, ticker, start_date, end_date):
        raise RuntimeError("resource limit test provider")


def make_client(monkeypatch):
    monkeypatch.setenv("INTERNAL_API_TOKEN", TOKEN)
    from app.main import create_app

    return TestClient(create_app(lambda: NoNetworkProvider()))


def test_fetch_accepts_exact_five_calendar_year_range(monkeypatch):
    client = make_client(monkeypatch)
    body = {
        "requestId": "limit-fetch-1",
        "ticker": "7203.T",
        "exchange": "XTKS",
        "timeZone": "Asia/Tokyo",
        "startDate": "2020-01-01",
        "endDate": "2025-01-01",
        "includePreviousSession": True,
        "priceBasis": "PROVIDER_ADJUSTED_CLOSE",
        "provider": "YFINANCE",
    }

    response = client.post(
        "/internal/v1/prices/fetch",
        headers=HEADERS,
        json=body,
    )

    assert response.status_code != 422 or response.json()["code"] != "VALIDATION_ERROR"


def test_fetch_rejects_more_than_five_calendar_years(monkeypatch):
    client = make_client(monkeypatch)
    body = {
        "requestId": "limit-fetch-2",
        "ticker": "7203.T",
        "exchange": "XTKS",
        "timeZone": "Asia/Tokyo",
        "startDate": "2020-01-01",
        "endDate": "2025-01-02",
        "includePreviousSession": True,
        "priceBasis": "PROVIDER_ADJUSTED_CLOSE",
        "provider": "YFINANCE",
    }

    response = client.post(
        "/internal/v1/prices/fetch",
        headers=HEADERS,
        json=body,
    )

    assert response.status_code == 422
    assert response.json()["code"] == "VALIDATION_ERROR"


def test_exact_body_limit_is_not_rejected_for_size(monkeypatch):
    client = make_client(monkeypatch)
    body = b"x" * MAX_JSON_BYTES

    response = client.post(
        "/internal/v1/health",
        headers={**HEADERS, "Content-Type": "application/json"},
        content=body,
    )

    assert response.status_code != 413


def test_body_over_limit_returns_error_response(monkeypatch):
    client = make_client(monkeypatch)
    request_id = "too-large-request"
    body = b"x" * (MAX_JSON_BYTES + 1)

    response = client.post(
        "/internal/v1/health",
        headers={
            **HEADERS,
            "Content-Type": "application/json",
            "X-Request-Id": request_id,
        },
        content=body,
    )

    assert response.status_code == 413
    assert response.json()["code"] == "REQUEST_BODY_TOO_LARGE"
    assert response.json()["requestId"] == request_id
    assert response.headers["X-Request-Id"] == request_id
    assert body[:100] not in response.content


def test_declared_content_length_over_limit_is_rejected_early(monkeypatch):
    client = make_client(monkeypatch)

    response = client.post(
        "/internal/v1/health",
        headers={
            **HEADERS,
            "Content-Type": "application/json",
            "Content-Length": str(MAX_JSON_BYTES + 1),
        },
        content=b"{}",
    )

    assert response.status_code == 413
    assert response.json()["code"] == "REQUEST_BODY_TOO_LARGE"
