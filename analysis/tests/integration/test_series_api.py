from datetime import date
import json

import numpy as np
import pytest
from fastapi.testclient import TestClient

from app.core.state_classifier import STATE_ORDER
from app.data.payload import content_sha256, provider_version
from app.providers.base import PriceDataset, PricePoint
from app.schemas import PriceDatasetPayload
from tests.integration.test_analyze_api import (
    HEADERS,
    TOKEN,
    make_dataset_payload,
    make_request,
)


def make_series_request(dataset, required_version="msa-core-v1"):
    body = make_request(dataset)
    body["requiredEngineVersion"] = required_version
    return body


def make_client(monkeypatch):
    monkeypatch.setenv("INTERNAL_API_TOKEN", TOKEN)
    from app.main import create_app

    return TestClient(create_app())


def rebuild_payload(payload, changes):
    prices = []
    for index, item in enumerate(payload.prices):
        values = item.model_dump()
        values.update(changes.get(index, {}))
        prices.append(values)
    dataset = PriceDataset(
        payload.ticker,
        payload.provider,
        [
            PricePoint(
                item["date"],
                item["close"],
                item["adjustedClose"],
                item["volume"],
            )
            for item in prices
        ],
    )
    updated = payload.model_dump()
    updated["prices"] = prices
    updated["contentSha256"] = content_sha256(
        dataset,
        payload.providerVersion,
    )
    return PriceDatasetPayload(**updated)


def post_series(client, body, headers=None):
    return client.post(
        "/internal/v1/series",
        headers=headers or HEADERS,
        json=body,
    )


def test_series_returns_condition_period_points(monkeypatch):
    client = make_client(monkeypatch)
    dataset = make_dataset_payload()

    response = post_series(client, make_series_request(dataset))
    result = response.json()

    assert response.status_code == 200
    assert set(result) == {"priceBasis", "points", "engineVersion"}
    assert result["priceBasis"] == "PROVIDER_ADJUSTED_CLOSE"
    assert result["engineVersion"] == "msa-core-v1"
    assert len(result["points"]) == 31
    assert result["points"][0]["date"] == dataset.prices[1].date.isoformat()
    assert result["points"][-1]["date"] == dataset.prices[-1].date.isoformat()
    assert all(
        point["state"] in STATE_ORDER
        and np.isfinite(point["returnValue"])
        and len(point["close"].split(".")[1]) == 10
        and len(point["adjustedClose"].split(".")[1]) == 10
        for point in result["points"]
    )
    assert all(
        current["date"] > previous["date"]
        for previous, current in zip(result["points"], result["points"][1:])
    )


def test_series_requires_token_and_preserves_request_id(monkeypatch):
    client = make_client(monkeypatch)
    body = make_series_request(make_dataset_payload())
    request_id = "series-request-1"

    response = post_series(client, body, {"X-Request-Id": request_id})
    assert response.status_code == 401
    assert response.headers["X-Request-Id"] == request_id
    assert response.json()["requestId"] == request_id


def test_required_engine_version_must_match_current(monkeypatch):
    client = make_client(monkeypatch)
    body = make_series_request(make_dataset_payload(), "old-v1")

    response = post_series(client, body)

    assert response.status_code == 409
    assert response.json()["code"] == "ENGINE_VERSION_UNSUPPORTED"


def test_series_rejects_tampered_hash_and_condition_mismatch(monkeypatch):
    client = make_client(monkeypatch)
    dataset = make_dataset_payload()
    bad_hash = {**dataset.model_dump(), "contentSha256": "0" * 64}
    body = make_series_request(PriceDatasetPayload(**bad_hash))

    response = post_series(client, body)
    assert response.status_code == 409
    assert response.json()["code"] == "DATASET_HASH_MISMATCH"

    body = make_series_request(dataset)
    condition = body["condition"]
    condition["startDate"] = "2019-01-01"
    response = post_series(client, {**body, "condition": condition})
    assert response.status_code == 409
    assert response.json()["code"] == "DATASET_CONDITION_MISMATCH"


def test_series_uses_adjusted_close_not_close(monkeypatch):
    client = make_client(monkeypatch)
    dataset = make_dataset_payload()
    changed_close = rebuild_payload(
        dataset,
        {
            0: {"close": "999.0000000000"},
            1: {"close": "999.0000000000"},
        },
    )

    original = post_series(client, make_series_request(dataset)).json()
    changed = post_series(client, make_series_request(changed_close)).json()

    assert [point["returnValue"] for point in original["points"]] == [
        point["returnValue"] for point in changed["points"]
    ]
    assert [point["state"] for point in original["points"]] == [
        point["state"] for point in changed["points"]
    ]
    assert original["points"][0]["close"] != changed["points"][0]["close"]


def test_series_adjusted_close_change_changes_return_state(monkeypatch):
    client = make_client(monkeypatch)
    dataset = make_dataset_payload()
    changed_adjusted = rebuild_payload(
        dataset,
        {1: {"adjustedClose": "100.4000000000"}},
    )

    original = post_series(client, make_series_request(dataset)).json()
    changed = post_series(client, make_series_request(changed_adjusted)).json()

    assert original["points"][0]["state"] == "UP"
    assert changed["points"][0]["state"] == "FLAT"
    assert original["points"][0]["returnValue"] != changed["points"][0]["returnValue"]


@pytest.mark.parametrize(
    ("first", "second", "expected_state"),
    [("100.0000000000", "99.5000000000", "FLAT"),
     ("100.0000000000", "100.5000000000", "FLAT")],
)
def test_series_threshold_boundaries_are_flat(
    monkeypatch,
    first,
    second,
    expected_state,
):
    client = make_client(monkeypatch)
    dataset = make_dataset_payload()
    changed = rebuild_payload(
        dataset,
        {
            0: {"adjustedClose": first},
            1: {"adjustedClose": second},
        },
    )

    result = post_series(client, make_series_request(changed)).json()

    assert result["points"][0]["state"] == expected_state
    assert result["points"][0]["returnValue"] == pytest.approx(
        (float(second) - float(first)) / float(first)
    )


def test_series_rejects_missing_previous_price(monkeypatch):
    client = make_client(monkeypatch)
    dataset = make_dataset_payload()
    prices = dataset.prices[1:]
    reduced_dataset = PriceDataset(
        dataset.ticker,
        dataset.provider,
        [
            PricePoint(item.date, item.close, item.adjustedClose, item.volume)
            for item in prices
        ],
    )
    payload = dataset.model_dump()
    payload["coverageStart"] = prices[0].date
    payload["prices"] = [item.model_dump() for item in prices]
    payload["contentSha256"] = content_sha256(
        reduced_dataset,
        provider_version(dataset.provider),
    )

    response = post_series(
        client,
        {
            **make_series_request(PriceDatasetPayload(**payload)),
            "condition": make_series_request(dataset)["condition"],
        },
    )

    assert response.status_code == 409
    assert response.json()["code"] == "DATASET_CONDITION_MISMATCH"


def test_series_does_not_call_provider_or_analysis(monkeypatch):
    client = make_client(monkeypatch)
    body = make_series_request(make_dataset_payload())

    def fail(*args, **kwargs):
        raise AssertionError("unexpected calculation dependency")

    monkeypatch.setattr("yfinance.download", fail)
    monkeypatch.setattr(
        "app.providers.yfinance_provider.YFinanceProvider.fetch",
        fail,
    )
    monkeypatch.setattr(
        "app.services.analysis_service.AnalysisService.analyze",
        fail,
    )

    response = post_series(client, body)

    assert response.status_code == 200


def test_series_is_registered_in_openapi(monkeypatch):
    client = make_client(monkeypatch)

    schema = client.get("/openapi.json").json()
    operation = schema["paths"]["/internal/v1/series"]["post"]

    assert "SeriesInput" in json.dumps(operation)
    assert "CalculatedSeries" in json.dumps(operation)
