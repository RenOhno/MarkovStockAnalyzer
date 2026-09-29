from datetime import date, datetime, timezone
from decimal import Decimal
import json
from types import SimpleNamespace

import numpy as np
import pytest
from fastapi.testclient import TestClient

from app.core.state_classifier import STATE_ORDER
from app.data.calendar import sessions
from app.data.payload import (
    ADJUSTMENT_POLICY,
    EXCHANGE,
    NORMALIZATION_VERSION,
    PRICE_BASIS,
    PRICE_SCALE,
    ROUNDING_MODE,
    TIME_ZONE,
    calendar_version,
    content_sha256,
    metadata,
    normalize_price_dataset,
    provider_version,
)
from app.providers.base import PriceDataset, PricePoint
from app.schemas import (
    ENGINE_VERSION,
    AnalysisCondition,
    PriceDatasetMetadata,
    PriceDatasetPayload,
    PricePointPayload,
)


TOKEN = "analyze-test-token"
HEADERS = {"X-Internal-Token": TOKEN}


def make_dataset_payload(provider="YFINANCE", states=None):
    states = states or [0, 0, 1, 2, 0, 2, 1, 0]
    states = (states * ((31 + len(states) - 1) // len(states)))[:31]
    dates = sessions(date(2020, 1, 1), date(2022, 12, 31))[:32]
    prices = [Decimal("100")]
    for state in states:
        if state == 0:
            prices.append(prices[-1] * Decimal("1.01"))
        elif state == 1:
            prices.append(prices[-1])
        else:
            prices.append(prices[-1] * Decimal("0.98"))

    dataset = normalize_price_dataset(
        PriceDataset(
            ticker="7203.T",
            provider=provider,
            prices=[
                PricePoint(
                    day,
                    f"{price:.10f}",
                    f"{price:.10f}",
                    100,
                )
                for day, price in zip(dates, prices, strict=True)
            ],
        )
    )
    provider_version_value = provider_version(provider)
    metadata_value = metadata(
        dataset,
        {
            "interval": "1d",
            "auto_adjust": False,
            "actions": True,
            "repair": False,
            "rounding": False,
        },
        provider_version_value,
        calendar_version(),
    )
    return PriceDatasetPayload(
        ticker=dataset.ticker,
        exchange=EXCHANGE,
        timeZone=TIME_ZONE,
        priceBasis=PRICE_BASIS,
        provider=dataset.provider,
        providerVersion=provider_version_value,
        adjustmentPolicy=ADJUSTMENT_POLICY,
        fetchedAt=datetime.now(timezone.utc),
        coverageStart=dataset.prices[0].date,
        coverageEnd=dataset.prices[-1].date,
        contentSha256=content_sha256(dataset, provider_version_value),
        metadata=PriceDatasetMetadata(**metadata_value),
        prices=[
            PricePointPayload(
                date=price.date,
                close=price.close,
                adjustedClose=price.adjusted_close,
                volume=price.volume,
            )
            for price in dataset.prices
        ],
    )


def make_request(dataset, condition=None, engine_version=ENGINE_VERSION):
    condition = condition or AnalysisCondition(
        startDate=dataset.prices[1].date,
        endDate=dataset.prices[-1].date,
        lowerThreshold=-0.005,
        upperThreshold=0.005,
        stateCount=3,
        estimator="MLE_STRICT",
        windowMode="FULL",
        windowSize=None,
        horizons=[1, 3, 5, 10],
    )
    condition_payload = condition.model_dump(mode="json")
    condition_payload["lowerThreshold"] = float(condition.lowerThreshold)
    condition_payload["upperThreshold"] = float(condition.upperThreshold)
    return {
        "requestId": "analyze-request-1",
        "engineVersion": engine_version,
        "condition": condition_payload,
        "dataset": dataset.model_dump(mode="json"),
    }


def make_client(monkeypatch):
    monkeypatch.setenv("INTERNAL_API_TOKEN", TOKEN)
    from app.main import create_app

    return TestClient(create_app())


def post_analyze(client, body, headers=None):
    return client.post(
        "/internal/v1/analyze",
        headers=headers or HEADERS,
        json=body,
    )


def test_analyze_returns_available_calculated_analysis(monkeypatch):
    client = make_client(monkeypatch)
    body = make_request(make_dataset_payload())

    response = post_analyze(client, body)
    result = response.json()

    assert response.status_code == 200
    assert result["stateOrder"] == list(STATE_ORDER)
    assert result["sampleCount"] == 31
    assert result["transitionCount"] == 30
    assert sum(map(sum, result["transitionCounts"])) == 30
    assert result["currentState"] == "FLAT"
    assert result["engineVersion"] == ENGINE_VERSION
    assert result["predictionStatus"] == "AVAILABLE"
    assert [item["horizon"] for item in result["forecasts"]] == [1, 3, 5, 10]
    assert all(
        len(item["probabilities"]) == 3
        and np.isclose(sum(item["probabilities"]), 1.0)
        for item in result["forecasts"]
    )
    assert all(
        np.isclose(sum(row), 1.0)
        for row in result["transitionMatrix"]
    )
    assert "inputContentSha256" in result["runtime"]
    assert result["runtime"]["inputContentSha256"] == body["dataset"]["contentSha256"]


def test_analyze_requires_token_and_preserves_request_id(monkeypatch):
    client = make_client(monkeypatch)
    body = make_request(make_dataset_payload())
    request_id = "analyze-request-header"

    response = post_analyze(
        client,
        body,
        headers={"X-Request-Id": request_id},
    )
    assert response.status_code == 401
    assert response.json()["requestId"] == request_id
    assert response.headers["X-Request-Id"] == request_id

    response = post_analyze(
        client,
        body,
        headers={**HEADERS, "X-Request-Id": request_id},
    )
    assert response.status_code == 200
    assert response.headers["X-Request-Id"] == request_id


def test_tampered_hash_or_price_is_rejected(monkeypatch):
    client = make_client(monkeypatch)
    body = make_request(make_dataset_payload())

    bad_hash = {**body, "dataset": {**body["dataset"], "contentSha256": "0" * 64}}
    changed_price = {
        **body,
        "dataset": {
            **body["dataset"],
            "prices": [
                {**body["dataset"]["prices"][0], "close": "999.0000000000"},
                *body["dataset"]["prices"][1:],
            ],
        },
    }

    assert post_analyze(client, bad_hash).status_code == 409
    assert post_analyze(client, changed_price).status_code == 409
    assert post_analyze(client, bad_hash).json()["code"] == "DATASET_HASH_MISMATCH"


def test_condition_dataset_mismatch_and_missing_previous_are_rejected(monkeypatch):
    client = make_client(monkeypatch)
    dataset = make_dataset_payload()
    later_condition = AnalysisCondition(
        startDate=dataset.prices[1].date,
        endDate=date(2023, 1, 1),
        lowerThreshold=-0.005,
        upperThreshold=0.005,
        stateCount=3,
        estimator="MLE_STRICT",
        windowMode="FULL",
        windowSize=None,
        horizons=[1, 3, 5, 10],
    )
    missing_previous = PriceDatasetPayload(
        **{
            **dataset.model_dump(),
            "coverageStart": dataset.prices[1].date,
            "prices": dataset.prices[1:],
            "contentSha256": content_sha256(
                PriceDataset(
                    dataset.ticker,
                    dataset.provider,
                    [
                        PricePoint(
                            item.date,
                            item.close,
                            item.adjustedClose,
                            item.volume,
                        )
                        for item in dataset.prices[1:]
                    ],
                ),
                dataset.providerVersion,
            ),
        }
    )
    original_condition = AnalysisCondition(
        startDate=dataset.prices[1].date,
        endDate=dataset.prices[-1].date,
        lowerThreshold=-0.005,
        upperThreshold=0.005,
        stateCount=3,
        estimator="MLE_STRICT",
        windowMode="FULL",
        windowSize=None,
        horizons=[1, 3, 5, 10],
    )

    assert post_analyze(client, make_request(dataset, later_condition)).status_code == 409
    response = post_analyze(
        client,
        make_request(missing_previous, original_condition),
    )
    assert response.status_code == 409
    assert response.json()["code"] == "DATASET_CONDITION_MISMATCH"


def test_fewer_than_30_states_returns_insufficient_states(monkeypatch):
    client = make_client(monkeypatch)
    dataset = make_dataset_payload()
    condition = AnalysisCondition(
        startDate=dataset.prices[1].date,
        endDate=dataset.prices[29].date,
        lowerThreshold=-0.005,
        upperThreshold=0.005,
        stateCount=3,
        estimator="MLE_STRICT",
        windowMode="FULL",
        windowSize=None,
        horizons=[1, 3, 5, 10],
    )

    response = post_analyze(client, make_request(dataset, condition))

    assert response.status_code == 422
    assert response.json()["code"] == "INSUFFICIENT_STATES"


@pytest.mark.parametrize(
    "field,value",
    [
        ("stateCount", 2),
        ("estimator", "OTHER"),
        ("windowMode", "EXPANDING"),
        ("windowSize", 10),
        ("horizons", [1, 3, 5]),
        ("lowerThreshold", -1.0),
        ("upperThreshold", 1.0),
        ("lowerThreshold", -0.12345678901),
    ],
)
def test_invalid_condition_is_rejected(monkeypatch, field, value):
    client = make_client(monkeypatch)
    dataset = make_dataset_payload()
    condition = make_request(dataset)["condition"]
    condition[field] = value

    response = post_analyze(
        client,
        {**make_request(dataset), "condition": condition},
    )

    assert response.status_code == 422
    assert response.json()["code"] == "VALIDATION_ERROR"


def test_unknown_key_and_engine_version_are_rejected(monkeypatch):
    client = make_client(monkeypatch)
    body = make_request(make_dataset_payload())

    unknown = post_analyze(client, {**body, "unexpected": True})
    old_version = post_analyze(
        client,
        make_request(make_dataset_payload(), engine_version="old-v1"),
    )

    assert unknown.status_code == 422
    assert old_version.status_code == 409
    assert old_version.json()["code"] == "ENGINE_VERSION_UNSUPPORTED"


def test_zero_row_returns_unavailable_without_nan(monkeypatch):
    client = make_client(monkeypatch)
    body = make_request(make_dataset_payload(states=[0]))

    response = post_analyze(client, body)
    result = response.json()

    assert response.status_code == 200
    assert result["predictionStatus"] == "UNAVAILABLE"
    assert result["forecasts"] == []
    assert [None, None, None] in result["transitionMatrix"]
    assert any(
        warning["code"] == "ZERO_ROW_UNESTIMATED"
        for warning in result["warnings"]
    )
    assert "NaN" not in response.text
    assert "Infinity" not in response.text


def test_fixture_warning_and_yfinance_no_synthetic_warning(monkeypatch):
    client = make_client(monkeypatch)
    fixture_result = post_analyze(
        client,
        make_request(make_dataset_payload(provider="FIXTURE")),
    ).json()
    yfinance_result = post_analyze(
        client,
        make_request(make_dataset_payload(provider="YFINANCE")),
    ).json()

    assert any(
        warning["code"] == "SYNTHETIC_FIXTURE"
        for warning in fixture_result["warnings"]
    )
    assert not any(
        warning["code"] == "SYNTHETIC_FIXTURE"
        for warning in yfinance_result["warnings"]
    )


def test_analysis_invariant_failure_returns_safe_500(monkeypatch):
    client = make_client(monkeypatch)
    body = make_request(make_dataset_payload())
    broken_result = SimpleNamespace(
        state_order=STATE_ORDER,
        sample_count=31,
        transition_count=1,
        transition_counts=[[0, 0, 0], [0, 0, 0], [0, 0, 0]],
        transition_matrix=[[1.0, 0.0, 0.0]] * 3,
        prediction_status="AVAILABLE",
        forecasts=[],
    )
    monkeypatch.setattr(
        "app.api.routes.AnalysisService.analyze",
        lambda *args, **kwargs: broken_result,
    )

    response = post_analyze(client, body)

    assert response.status_code == 500
    assert response.json()["code"] == "CALCULATION_INVARIANT_FAILED"
    assert "SimpleNamespace" not in response.text
    assert "analysis_adapter.py" not in response.text


def test_analyze_is_registered_in_openapi(monkeypatch):
    client = make_client(monkeypatch)

    schema = client.get("/openapi.json").json()
    operation = schema["paths"]["/internal/v1/analyze"]["post"]

    assert "AnalyzeInput" in str(operation)
    assert "CalculatedAnalysis" in str(operation)


def test_analyze_does_not_call_provider_or_network(monkeypatch):
    client = make_client(monkeypatch)
    body = make_request(make_dataset_payload())

    def fail(*args, **kwargs):
        raise AssertionError("provider must not be called")

    monkeypatch.setattr("yfinance.download", fail)
    monkeypatch.setattr(
        "app.providers.yfinance_provider.YFinanceProvider.fetch",
        fail,
    )

    response = post_analyze(client, body)

    assert response.status_code == 200


@pytest.mark.parametrize("value", [float("nan"), float("inf")])
def test_non_finite_threshold_is_rejected(monkeypatch, value):
    client = make_client(monkeypatch)
    body = make_request(make_dataset_payload())
    body["condition"]["lowerThreshold"] = value

    response = client.post(
        "/internal/v1/analyze",
        headers=HEADERS,
        content=json.dumps(body, allow_nan=True),
    )

    assert response.status_code == 422
    assert response.json()["code"] == "VALIDATION_ERROR"
