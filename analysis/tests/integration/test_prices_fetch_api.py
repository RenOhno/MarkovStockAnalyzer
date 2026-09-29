from datetime import date
import json

from fastapi.testclient import TestClient
import pytest

from app.providers.base import MarketDataProvider, PriceDataset, PricePoint


TOKEN = "prices-test-token"
HEADERS = {"X-Internal-Token": TOKEN}
REQUEST = {
    "requestId": "prices-request-1",
    "ticker": "7203.T",
    "exchange": "XTKS",
    "timeZone": "Asia/Tokyo",
    "startDate": "2026-01-06",
    "endDate": "2026-01-07",
    "includePreviousSession": True,
    "priceBasis": "PROVIDER_ADJUSTED_CLOSE",
    "provider": "YFINANCE",
}


class FakeProvider(MarketDataProvider):
    def __init__(self, prices=None):
        self.calls = []
        self.prices = prices or [
            PricePoint(date(2026, 1, 5), "100", "99.5", 1000),
            PricePoint(date(2026, 1, 6), "101", "100.5", None),
            PricePoint(date(2026, 1, 7), "102", "101.5", 1200),
        ]

    def fetch(self, ticker, start_date, end_date):
        self.calls.append((ticker, start_date, end_date))
        return PriceDataset(ticker, "YFINANCE", self.prices)


class RaisingProvider(MarketDataProvider):
    def __init__(self, error):
        self.error = error

    def fetch(self, ticker, start_date, end_date):
        raise self.error


def make_client(monkeypatch, provider):
    monkeypatch.setenv("INTERNAL_API_TOKEN", TOKEN)
    from app.main import create_app

    return TestClient(create_app(lambda: provider))


def test_fetch_returns_normalized_price_dataset_payload(monkeypatch):
    provider = FakeProvider()
    client = make_client(monkeypatch, provider)

    response = client.post(
        "/internal/v1/prices/fetch",
        headers=HEADERS,
        json=REQUEST,
    )
    body = response.json()

    assert response.status_code == 200
    assert body["ticker"] == "7203.T"
    assert body["exchange"] == "XTKS"
    assert body["timeZone"] == "Asia/Tokyo"
    assert body["priceBasis"] == "PROVIDER_ADJUSTED_CLOSE"
    assert body["provider"] == "YFINANCE"
    assert body["adjustmentPolicy"] == "PROVIDER_ADJUSTED_CLOSE_V1"
    assert body["coverageStart"] == "2026-01-05"
    assert body["coverageEnd"] == "2026-01-07"
    assert [price["date"] for price in body["prices"]] == [
        "2026-01-05",
        "2026-01-06",
        "2026-01-07",
    ]
    assert body["prices"][0]["close"] == "100.0000000000"
    assert body["prices"][0]["adjustedClose"] == "99.5000000000"
    assert body["prices"][1]["volume"] is None
    assert isinstance(body["prices"][0]["volume"], int)


def test_fetch_uses_previous_xtks_session(monkeypatch):
    provider = FakeProvider()
    client = make_client(monkeypatch, provider)

    response = client.post(
        "/internal/v1/prices/fetch",
        headers=HEADERS,
        json=REQUEST,
    )

    assert response.status_code == 200
    assert provider.calls == [("7203.T", date(2026, 1, 5), date(2026, 1, 7))]


def test_content_hash_is_reproducible_and_excludes_fetched_at(monkeypatch):
    first = make_client(monkeypatch, FakeProvider()).post(
        "/internal/v1/prices/fetch",
        headers=HEADERS,
        json=REQUEST,
    ).json()
    second = make_client(monkeypatch, FakeProvider()).post(
        "/internal/v1/prices/fetch",
        headers=HEADERS,
        json=REQUEST,
    ).json()

    assert first["contentSha256"] == second["contentSha256"]
    assert first["fetchedAt"] != second["fetchedAt"]
    assert len(first["contentSha256"]) == 64
    assert first["contentSha256"] == first["contentSha256"].lower()
    assert first["fetchedAt"] != ""


def test_metadata_contains_designated_normalization_fields(monkeypatch):
    client = make_client(monkeypatch, FakeProvider())

    body = client.post(
        "/internal/v1/prices/fetch",
        headers=HEADERS,
        json=REQUEST,
    ).json()
    metadata = body["metadata"]

    assert metadata["schemaVersion"] == 1
    assert metadata["calendarName"] == "XTKS"
    assert metadata["roundingMode"] == "ROUND_HALF_UP"
    assert metadata["scale"] == 10
    assert metadata["fetchOptions"]["interval"] == "1d"
    assert metadata["qualityFlags"] == []


@pytest.mark.parametrize(
    "field,value",
    [
        ("exchange", "NYSE"),
        ("timeZone", "UTC"),
        ("includePreviousSession", False),
        ("priceBasis", "CLOSE"),
        ("provider", "FIXTURE"),
    ],
)
def test_unsupported_request_values_return_validation_error(
    monkeypatch,
    field,
    value,
):
    client = make_client(monkeypatch, FakeProvider())
    request = {**REQUEST, field: value}

    response = client.post(
        "/internal/v1/prices/fetch",
        headers=HEADERS,
        json=request,
    )

    assert response.status_code == 422
    assert response.json()["code"] == "VALIDATION_ERROR"


def test_unknown_key_and_invalid_ticker_are_rejected(monkeypatch):
    client = make_client(monkeypatch, FakeProvider())

    unknown = client.post(
        "/internal/v1/prices/fetch",
        headers=HEADERS,
        json={**REQUEST, "unexpected": True},
    )
    invalid_ticker = client.post(
        "/internal/v1/prices/fetch",
        headers=HEADERS,
        json={**REQUEST, "ticker": "https://example.test"},
    )

    assert unknown.status_code == 422
    assert invalid_ticker.status_code == 422
    assert unknown.json()["code"] == "VALIDATION_ERROR"
    assert invalid_ticker.json()["code"] == "VALIDATION_ERROR"


def test_invalid_date_range_and_broken_json_are_distinguished(monkeypatch):
    client = make_client(monkeypatch, FakeProvider())

    invalid_range = client.post(
        "/internal/v1/prices/fetch",
        headers=HEADERS,
        json={**REQUEST, "startDate": "2026-01-08"},
    )
    broken_json = client.post(
        "/internal/v1/prices/fetch",
        headers={**HEADERS, "Content-Type": "application/json"},
        content="{",
    )

    assert invalid_range.status_code == 422
    assert invalid_range.json()["code"] == "VALIDATION_ERROR"
    assert broken_json.status_code == 400
    assert broken_json.json()["code"] == "INVALID_JSON"


def test_provider_empty_data_maps_to_no_price_data(monkeypatch):
    client = make_client(monkeypatch, RaisingProvider(ValueError("NO_PRICE_DATA")))

    response = client.post(
        "/internal/v1/prices/fetch",
        headers=HEADERS,
        json=REQUEST,
    )

    assert response.status_code == 422
    assert response.json()["code"] == "NO_PRICE_DATA"


def test_invalid_price_maps_to_invalid_price_data(monkeypatch):
    prices = [PricePoint(date(2026, 1, 5), "0", "99", 1)]
    client = make_client(monkeypatch, FakeProvider(prices))

    response = client.post(
        "/internal/v1/prices/fetch",
        headers=HEADERS,
        json=REQUEST,
    )

    assert response.status_code == 422
    assert response.json()["code"] == "INVALID_PRICE_DATA"


def test_data_gap_maps_to_data_gap(monkeypatch):
    prices = [
        PricePoint(date(2026, 1, 5), "100", "99", 1),
        PricePoint(date(2026, 1, 7), "102", "101", 1),
    ]
    client = make_client(monkeypatch, FakeProvider(prices))

    response = client.post(
        "/internal/v1/prices/fetch",
        headers=HEADERS,
        json=REQUEST,
    )

    assert response.status_code == 422
    assert response.json()["code"] == "DATA_GAP"


@pytest.mark.parametrize(
    ("error", "status_code", "code"),
    [
        (ConnectionError("provider secret URL"), 502, "PROVIDER_UNAVAILABLE"),
        (TimeoutError("private timeout details"), 504, "PROVIDER_TIMEOUT"),
        (RuntimeError("provider HTML and cookie"), 502, "PROVIDER_UNAVAILABLE"),
    ],
)
def test_provider_errors_are_safe_and_have_request_id(
    monkeypatch,
    error,
    status_code,
    code,
):
    client = make_client(monkeypatch, RaisingProvider(error))
    request_id = "fetch-error-1"

    response = client.post(
        "/internal/v1/prices/fetch",
        headers={**HEADERS, "X-Request-Id": request_id},
        json=REQUEST,
    )
    body = response.json()

    assert response.status_code == status_code
    assert body["code"] == code
    assert body["requestId"] == request_id
    assert response.headers["X-Request-Id"] == request_id
    assert "provider secret URL" not in response.text
    assert "private timeout details" not in response.text
    assert "provider HTML and cookie" not in response.text


def test_fetch_requires_internal_token(monkeypatch):
    client = make_client(monkeypatch, FakeProvider())

    response = client.post(
        "/internal/v1/prices/fetch",
        json=REQUEST,
    )

    assert response.status_code == 401
    assert response.json()["code"] == "INTERNAL_AUTH_FAILED"


def test_openapi_registers_fetch_request_and_response(monkeypatch):
    client = make_client(monkeypatch, FakeProvider())

    schema = client.get("/openapi.json").json()
    operation = schema["paths"]["/internal/v1/prices/fetch"]["post"]

    assert "requestBody" in operation
    assert "PriceDatasetPayload" in json.dumps(operation)
