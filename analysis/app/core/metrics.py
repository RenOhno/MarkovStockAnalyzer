from collections import Counter
from dataclasses import dataclass
import math
from typing import Sequence

import numpy as np

from app.core.backtest import (
    STATUS_SCORED,
    STATUS_SKIPPED,
    BacktestPrediction,
    BacktestResult,
)
from app.core.state_classifier import STATE_ORDER


@dataclass(frozen=True)
class MetricsResult:
    eligible_count: int
    predicted_count: int
    skipped_count: int
    correct_count: int
    coverage: float
    accuracy: float | None
    precision: list[float | None]
    recall: list[float | None]
    confusion_matrix: list[list[int]]
    brier_score: float | None
    log_loss: float | None
    skip_reasons: dict[str, int]
    tie_count: int


def evaluate_predictions(result: BacktestResult) -> MetricsResult:
    """バックテストの日別明細から基本評価指標を計算する。"""
    predictions = list(result.predictions)
    if not predictions:
        raise ValueError("EMPTY_BACKTEST_RESULT")

    _validate_predictions(predictions)

    scored = [
        prediction
        for prediction in predictions
        if prediction.status == STATUS_SCORED
    ]
    skipped = [
        prediction
        for prediction in predictions
        if prediction.status == STATUS_SKIPPED
    ]

    eligible_count = len(predictions)
    predicted_count = len(scored)
    skipped_count = len(skipped)
    correct_count = sum(
        prediction.predicted_state == prediction.actual_state
        for prediction in scored
    )

    confusion_matrix = _confusion_matrix(scored)
    precision = _precision(scored)
    recall = _recall(scored)

    return MetricsResult(
        eligible_count=eligible_count,
        predicted_count=predicted_count,
        skipped_count=skipped_count,
        correct_count=correct_count,
        coverage=predicted_count / eligible_count,
        accuracy=(
            correct_count / predicted_count
            if predicted_count > 0
            else None
        ),
        precision=precision,
        recall=recall,
        confusion_matrix=confusion_matrix,
        brier_score=_brier_score(scored),
        log_loss=_log_loss(scored),
        skip_reasons=dict(
            Counter(prediction.skip_code for prediction in skipped)
        ),
        tie_count=sum(_has_probability_tie(prediction) for prediction in scored),
    )


def _validate_predictions(predictions: Sequence[BacktestPrediction]) -> None:
    for prediction in predictions:
        if prediction.actual_state not in STATE_ORDER:
            raise ValueError("INVALID_ACTUAL_STATE")

        if prediction.status == STATUS_SCORED:
            if prediction.predicted_state not in STATE_ORDER:
                raise ValueError("INVALID_PREDICTED_STATE")
            if prediction.probabilities is None:
                raise ValueError("MISSING_PROBABILITIES")
            if len(prediction.probabilities) != len(STATE_ORDER):
                raise ValueError("INVALID_PROBABILITIES")
            probabilities = np.asarray(prediction.probabilities, dtype=float)
            if not np.isfinite(probabilities).all():
                raise ValueError("INVALID_PROBABILITIES")
            if np.any(probabilities < 0):
                raise ValueError("INVALID_PROBABILITIES")
            if not np.isclose(
                probabilities.sum(),
                1.0,
                atol=1e-12,
                rtol=0,
            ):
                raise ValueError("INVALID_PROBABILITIES")
            if prediction.skip_code is not None:
                raise ValueError("INVALID_SCORED_SKIP_CODE")
        elif prediction.status == STATUS_SKIPPED:
            if prediction.predicted_state is not None:
                raise ValueError("INVALID_SKIPPED_PREDICTED_STATE")
            if prediction.probabilities is not None:
                raise ValueError("INVALID_SKIPPED_PROBABILITIES")
            if not prediction.skip_code:
                raise ValueError("MISSING_SKIP_CODE")
        else:
            raise ValueError("INVALID_PREDICTION_STATUS")


def _state_index(state: str) -> int:
    return STATE_ORDER.index(state)


def _confusion_matrix(
    scored: Sequence[BacktestPrediction],
) -> list[list[int]]:
    matrix = np.zeros((len(STATE_ORDER), len(STATE_ORDER)), dtype=np.int64)
    for prediction in scored:
        matrix[
            _state_index(prediction.actual_state),
            _state_index(prediction.predicted_state),
        ] += 1
    return matrix.tolist()


def _precision(scored: Sequence[BacktestPrediction]) -> list[float | None]:
    values = []
    for state in STATE_ORDER:
        predicted = [
            prediction
            for prediction in scored
            if prediction.predicted_state == state
        ]
        if not predicted:
            values.append(None)
            continue
        values.append(
            sum(
                prediction.actual_state == state
                for prediction in predicted
            )
            / len(predicted)
        )
    return values


def _recall(scored: Sequence[BacktestPrediction]) -> list[float | None]:
    values = []
    for state in STATE_ORDER:
        actual = [
            prediction
            for prediction in scored
            if prediction.actual_state == state
        ]
        if not actual:
            values.append(None)
            continue
        values.append(
            sum(
                prediction.predicted_state == state
                for prediction in actual
            )
            / len(actual)
        )
    return values


def _brier_score(
    scored: Sequence[BacktestPrediction],
) -> float | None:
    if not scored:
        return None

    scores = []
    for prediction in scored:
        actual = np.zeros(len(STATE_ORDER), dtype=float)
        actual[_state_index(prediction.actual_state)] = 1.0
        probabilities = np.asarray(prediction.probabilities, dtype=float)
        scores.append(float(np.sum((probabilities - actual) ** 2)))
    return sum(scores) / len(scores)


def _log_loss(
    scored: Sequence[BacktestPrediction],
) -> float | None:
    if not scored:
        return None

    losses = []
    for prediction in scored:
        actual_probability = prediction.probabilities[
            _state_index(prediction.actual_state)
        ]
        losses.append(-math.log(max(actual_probability, 1e-15)))
    return sum(losses) / len(losses)


def _has_probability_tie(prediction: BacktestPrediction) -> bool:
    probabilities = np.asarray(prediction.probabilities, dtype=float)
    maximum = probabilities.max()
    return int(
        np.isclose(probabilities, maximum, atol=1e-12, rtol=0).sum()
    ) > 1
