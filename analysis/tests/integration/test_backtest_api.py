from datetime import date
import json

import numpy as np
import pytest
from fastapi.testclient import TestClient

from app.core.backtest import BacktestResult
from app.schemas import AnalysisCondition
from tests.integration.test_analyze_api import (
    HEADERS,
    TOKEN,
    make_dataset_payload,
    make_request,
)


def make_backtest_request(dataset, test_start_index=31, test_end_index=35):
    condition = AnalysisCondition(
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
    body = make_request(dataset, condition)
    body["evaluation"] = {
        "testStart": dataset.prices[test_start_index].date.isoformat(),
        "testEnd": dataset.prices[test_end_index].date.isoformat(),
        "minTrainStates": 30,
        "trainingMode": "EXPANDING",
        "windowSize": None,
        "horizon": 1,
    }
    return body


def make_client(monkeypatch):
    monkeypatch.setenv("INTERNAL_API_TOKEN", TOKEN)
    from app.main import create_app

    return TestClient(create_app())


def test_backtest_returns_summary_predictions_and_metrics(monkeypatch):
    client = make_client(monkeypatch)
    body = make_backtest_request(make_dataset_payload(state_count=40))

    response = client.post(
        "/internal/v1/backtest",
        headers=HEADERS,
        json=body,
    )
    result = response.json()

    assert response.status_code == 200
    summary = result["summary"]
    assert summary["horizon"] == 1
    assert summary["eligibleCount"] == 5
    assert len(result["predictions"]) == summary["eligibleCount"]
    assert summary["predictedCount"] + summary["skippedCount"] == 5
    assert summary["coverage"] == pytest.approx(
        summary["predictedCount"] / summary["eligibleCount"]
    )
    assert set(result) == {"summary", "predictions", "engineVersion", "runtime"}
    assert set(summary["metrics"]) == {
        "accuracy",
        "precision",
        "recall",
        "confusionMatrix",
        "brierScore",
        "logLoss",
        "majorityAccuracy",
        "persistenceAccuracy",
        "tieCount",
        "skipReasons",
    }
    assert result["engineVersion"] == "msa-core-v1"

    for prediction in result["predictions"]:
        assert prediction["actualState"] in {"UP", "FLAT", "DOWN"}
        if prediction["status"] == "SCORED":
            assert len(prediction["probabilities"]) == 3
            assert np.isclose(sum(prediction["probabilities"]), 1.0)
            assert prediction["majorityState"] is not None
            assert prediction["persistenceState"] is not None
            assert prediction["skipCode"] is None
        else:
            assert prediction["predictedState"] is None
            assert prediction["probabilities"] is None
            assert prediction["majorityState"] is None
            assert prediction["persistenceState"] is None
            assert prediction["skipCode"] is not None


def test_backtest_requires_token_and_preserves_request_id(monkeypatch):
    client = make_client(monkeypatch)
    body = make_backtest_request(make_dataset_payload(state_count=40))
    request_id = "backtest-request-1"

    response = client.post(
        "/internal/v1/backtest",
        headers={"X-Request-Id": request_id},
        json=body,
    )

    assert response.status_code == 401
    assert response.headers["X-Request-Id"] == request_id
    assert response.json()["requestId"] == request_id


def test_backtest_rejects_invalid_evaluation_values(monkeypatch):
    client = make_client(monkeypatch)
    body = make_backtest_request(make_dataset_payload(state_count=40))

    cases = [
        {"minTrainStates": 29},
        {"trainingMode": "ROLLING"},
        {"windowSize": 10},
        {"horizon": 3},
        {"testStart": "2021-01-01", "testEnd": "2020-01-01"},
    ]
    for change in cases:
        evaluation = {**body["evaluation"], **change}
        response = client.post(
            "/internal/v1/backtest",
            headers=HEADERS,
            json={**body, "evaluation": evaluation},
        )
        assert response.status_code == 422
        assert response.json()["code"] == "VALIDATION_ERROR"


def test_backtest_rejects_evaluation_outside_condition(monkeypatch):
    client = make_client(monkeypatch)
    dataset = make_dataset_payload(state_count=40)
    body = make_backtest_request(dataset)
    body["evaluation"]["testStart"] = "2019-01-01"

    response = client.post(
        "/internal/v1/backtest",
        headers=HEADERS,
        json=body,
    )

    assert response.status_code == 422
    assert response.json()["code"] == "EVALUATION_RANGE_INVALID"


def test_backtest_rejects_insufficient_training_before_first_target(monkeypatch):
    client = make_client(monkeypatch)
    dataset = make_dataset_payload(state_count=40)
    body = make_backtest_request(dataset, test_start_index=2, test_end_index=3)

    response = client.post(
        "/internal/v1/backtest",
        headers=HEADERS,
        json=body,
    )

    assert response.status_code == 422
    assert response.json()["code"] == "INSUFFICIENT_TRAINING_DATA"


def test_backtest_rejects_tampered_dataset_hash(monkeypatch):
    client = make_client(monkeypatch)
    body = make_backtest_request(make_dataset_payload(state_count=40))
    body["dataset"]["contentSha256"] = "0" * 64

    response = client.post(
        "/internal/v1/backtest",
        headers=HEADERS,
        json=body,
    )

    assert response.status_code == 409
    assert response.json()["code"] == "DATASET_HASH_MISMATCH"


def test_all_unestimated_origins_are_skipped_and_metrics_are_null(monkeypatch):
    client = make_client(monkeypatch)
    body = make_backtest_request(
        make_dataset_payload(states=[0], state_count=40)
    )

    response = client.post(
        "/internal/v1/backtest",
        headers=HEADERS,
        json=body,
    )
    result = response.json()

    assert response.status_code == 200
    assert result["summary"]["predictedCount"] == 0
    assert result["summary"]["skippedCount"] == 5
    assert result["summary"]["metrics"]["accuracy"] is None
    assert result["summary"]["metrics"]["brierScore"] is None
    assert result["summary"]["metrics"]["logLoss"] is None
    assert result["summary"]["metrics"]["confusionMatrix"] == [
        [0, 0, 0],
        [0, 0, 0],
        [0, 0, 0],
    ]
    assert all(
        prediction["status"] == "SKIPPED"
        and prediction["probabilities"] is None
        and prediction["majorityState"] is None
        and prediction["persistenceState"] is None
        and prediction["skipCode"] == "ZERO_ROW_UNESTIMATED"
        for prediction in result["predictions"]
    )


def test_future_price_changes_do_not_change_first_origin_prediction(monkeypatch):
    client = make_client(monkeypatch)
    prefix = [0, 1, 2] * 11
    first = make_dataset_payload(
        states=prefix + [0] * 9,
        state_count=40,
    )
    changed = make_dataset_payload(
        states=prefix + [2] * 9,
        state_count=40,
    )
    first_result = client.post(
        "/internal/v1/backtest",
        headers=HEADERS,
        json=make_backtest_request(first, 31, 33),
    ).json()
    changed_result = client.post(
        "/internal/v1/backtest",
        headers=HEADERS,
        json=make_backtest_request(changed, 31, 33),
    ).json()

    assert first_result["predictions"][0] == changed_result["predictions"][0]


def test_backtest_does_not_call_provider_or_network(monkeypatch):
    client = make_client(monkeypatch)
    body = make_backtest_request(make_dataset_payload(state_count=40))

    def fail(*args, **kwargs):
        raise AssertionError("provider must not be called")

    monkeypatch.setattr("yfinance.download", fail)
    monkeypatch.setattr(
        "app.providers.yfinance_provider.YFinanceProvider.fetch",
        fail,
    )

    response = client.post(
        "/internal/v1/backtest",
        headers=HEADERS,
        json=body,
    )

    assert response.status_code == 200


def test_backtest_internal_invariant_failure_returns_500(monkeypatch):
    client = make_client(monkeypatch)
    body = make_backtest_request(make_dataset_payload(state_count=40))
    monkeypatch.setattr(
        "app.api.routes.walk_forward",
        lambda *args, **kwargs: BacktestResult([]),
    )

    response = client.post(
        "/internal/v1/backtest",
        headers=HEADERS,
        json=body,
    )

    assert response.status_code == 500
    assert response.json()["code"] == "CALCULATION_INVARIANT_FAILED"
    assert "traceback" not in response.text.lower()


def test_backtest_is_registered_in_openapi(monkeypatch):
    client = make_client(monkeypatch)

    schema = client.get("/openapi.json").json()
    operation = schema["paths"]["/internal/v1/backtest"]["post"]

    assert "BacktestInput" in json.dumps(operation)
    assert "CalculatedBacktest" in json.dumps(operation)
