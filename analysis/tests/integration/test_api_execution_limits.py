from concurrent.futures import ThreadPoolExecutor
from datetime import date
from threading import Event

from fastapi.testclient import TestClient

from app.providers.base import MarketDataProvider, PriceDataset, PricePoint
from tests.integration.test_analyze_api import make_dataset_payload, make_request


TOKEN = "execution-limit-token"
HEADERS = {"X-Internal-Token": TOKEN}
FETCH_REQUEST = {
    "requestId": "execution-fetch",
    "ticker": "7203.T",
    "exchange": "XTKS",
    "timeZone": "Asia/Tokyo",
    "startDate": "2026-01-06",
    "endDate": "2026-01-07",
    "includePreviousSession": True,
    "priceBasis": "PROVIDER_ADJUSTED_CLOSE",
    "provider": "YFINANCE",
}


class SlowProvider(MarketDataProvider):
    def __init__(self):
        self.started = Event()
        self.release = Event()
        self.calls = 0

    def fetch(self, ticker, start_date, end_date):
        self.calls += 1
        self.started.set()
        self.release.wait()
        return PriceDataset(
            ticker,
            "YFINANCE",
            [
                PricePoint(date(2026, 1, 5), "100", "99.5", 1),
                PricePoint(date(2026, 1, 6), "101", "100.5", 1),
                PricePoint(date(2026, 1, 7), "102", "101.5", 1),
            ],
        )


def make_client(monkeypatch, provider, deadline=12.0):
    monkeypatch.setenv("INTERNAL_API_TOKEN", TOKEN)
    from app.main import create_app

    return TestClient(
        create_app(
            provider_factory=lambda: provider,
            fetch_deadline_seconds=deadline,
        )
    )


def test_shared_gate_rejects_second_request_and_keeps_health_available(monkeypatch):
    provider = SlowProvider()
    client = make_client(monkeypatch, provider)

    with ThreadPoolExecutor(max_workers=1) as requests:
        first = requests.submit(
            client.post,
            "/internal/v1/prices/fetch",
            headers=HEADERS,
            json=FETCH_REQUEST,
        )
        assert provider.started.wait(1)

        second = client.post(
            "/internal/v1/prices/fetch",
            headers={**HEADERS, "X-Request-Id": "second-request"},
            json=FETCH_REQUEST,
        )
        health = client.get("/internal/v1/health", headers=HEADERS)

        assert second.status_code == 429
        assert second.json()["code"] == "TOO_MANY_ANALYSES"
        assert second.json()["requestId"] == "second-request"
        assert second.headers["X-Request-Id"] == "second-request"
        assert health.status_code == 200

        provider.release.set()
        assert first.result().status_code == 200

    third = client.post(
        "/internal/v1/prices/fetch",
        headers=HEADERS,
        json=FETCH_REQUEST,
    )
    assert third.status_code == 200
    assert provider.calls == 2


def test_timeout_keeps_gate_until_provider_worker_finishes(monkeypatch):
    provider = SlowProvider()
    client = make_client(monkeypatch, provider, deadline=0.05)

    with ThreadPoolExecutor(max_workers=1) as requests:
        first = requests.submit(
            client.post,
            "/internal/v1/prices/fetch",
            headers={**HEADERS, "X-Request-Id": "timeout-request"},
            json=FETCH_REQUEST,
        )
        assert provider.started.wait(1)
        timeout_response = first.result()

    assert timeout_response.status_code == 504
    assert timeout_response.json()["code"] == "PROVIDER_TIMEOUT"
    assert timeout_response.json()["requestId"] == "timeout-request"
    assert timeout_response.headers["X-Request-Id"] == "timeout-request"

    blocked = client.post(
        "/internal/v1/prices/fetch",
        headers=HEADERS,
        json=FETCH_REQUEST,
    )
    assert blocked.status_code == 429
    assert provider.calls == 1

    provider.release.set()
    completed = client.post(
        "/internal/v1/prices/fetch",
        headers=HEADERS,
        json=FETCH_REQUEST,
    )
    assert completed.status_code == 200
    assert provider.calls == 2


def test_gate_is_shared_between_fetch_and_analyze(monkeypatch):
    provider = SlowProvider()
    client = make_client(monkeypatch, provider)
    analyze_body = make_request(make_dataset_payload())

    with ThreadPoolExecutor(max_workers=1) as requests:
        first = requests.submit(
            client.post,
            "/internal/v1/prices/fetch",
            headers=HEADERS,
            json=FETCH_REQUEST,
        )
        assert provider.started.wait(1)

        blocked = client.post(
            "/internal/v1/analyze",
            headers=HEADERS,
            json=analyze_body,
        )
        assert blocked.status_code == 429
        assert blocked.json()["code"] == "TOO_MANY_ANALYSES"

        provider.release.set()
        assert first.result().status_code == 200


def test_exception_releases_shared_gate(monkeypatch):
    provider = SlowProvider()
    client = make_client(monkeypatch, provider)
    analyze_body = make_request(make_dataset_payload())

    def fail(*args, **kwargs):
        raise RuntimeError("expected test failure")

    monkeypatch.setattr(
        "app.services.analysis_service.AnalysisService.analyze",
        fail,
    )

    first = client.post(
        "/internal/v1/analyze",
        headers=HEADERS,
        json=analyze_body,
    )
    second = client.post(
        "/internal/v1/analyze",
        headers=HEADERS,
        json=analyze_body,
    )

    assert first.status_code == 500
    assert second.status_code == 500
