from datetime import date, timedelta
import math

import numpy as np
import pytest

from app.core.backtest import (
    BacktestPrediction,
    BacktestResult,
    STATUS_SCORED,
    STATUS_SKIPPED,
)
from app.core.metrics import evaluate_predictions


def prediction(
    actual,
    predicted,
    probabilities,
    status=STATUS_SCORED,
    skip_code=None,
    index=0,
):
    current = date(2026, 1, 1) + timedelta(days=index)
    return BacktestPrediction(
        origin_date=current,
        target_date=current + timedelta(days=1),
        train_start=date(2025, 1, 1),
        train_end=current,
        actual_state=actual,
        predicted_state=predicted,
        probabilities=probabilities,
        status=status,
        skip_code=skip_code,
    )


def scored_predictions():
    return [
        prediction("UP", "UP", [0.7, 0.2, 0.1]),
        prediction("FLAT", "UP", [0.6, 0.3, 0.1], index=1),
        prediction("DOWN", "DOWN", [0.1, 0.2, 0.7], index=2),
        prediction("UP", "FLAT", [0.2, 0.5, 0.3], index=3),
    ]


def test_basic_counts_coverage_accuracy_and_class_metrics():
    records = scored_predictions() + [
        prediction(
            "DOWN",
            None,
            None,
            STATUS_SKIPPED,
            "ZERO_ROW_UNESTIMATED",
            4,
        ),
        prediction(
            "FLAT",
            None,
            None,
            STATUS_SKIPPED,
            "ZERO_ROW_UNESTIMATED",
            5,
        ),
    ]

    result = evaluate_predictions(BacktestResult(records))

    assert result.eligible_count == 6
    assert result.predicted_count == 4
    assert result.skipped_count == 2
    assert result.correct_count == 2
    assert result.coverage == pytest.approx(2 / 3)
    assert result.accuracy == pytest.approx(0.5)
    assert result.precision == pytest.approx([0.5, 0.0, 1.0])
    assert result.recall == pytest.approx([0.5, 0.0, 1.0])
    assert result.skip_reasons == {"ZERO_ROW_UNESTIMATED": 2}


def test_confusion_matrix_is_actual_by_predicted():
    result = evaluate_predictions(BacktestResult(scored_predictions()))

    assert result.confusion_matrix == [
        [1, 1, 0],
        [1, 0, 0],
        [0, 0, 1],
    ]
    assert sum(map(sum, result.confusion_matrix)) == result.predicted_count
    assert sum(
        result.confusion_matrix[index][index]
        for index in range(3)
    ) == result.correct_count


def test_brier_score_is_not_divided_by_class_count():
    result = evaluate_predictions(BacktestResult(scored_predictions()))

    expected = (0.14 + 0.86 + 0.14 + 0.98) / 4
    assert result.brier_score == pytest.approx(expected)
    assert result.brier_score != pytest.approx(expected / 3)


def test_log_loss_matches_manual_calculation():
    result = evaluate_predictions(BacktestResult(scored_predictions()))

    expected = -(
        math.log(0.7)
        + math.log(0.3)
        + math.log(0.7)
        + math.log(0.2)
    ) / 4
    assert result.log_loss == pytest.approx(expected)


def test_zero_actual_probability_is_clipped_without_mutating_input():
    probabilities = [0.0, 1.0, 0.0]
    record = prediction("UP", "FLAT", probabilities)

    result = evaluate_predictions(BacktestResult([record]))

    assert result.log_loss == pytest.approx(-math.log(1e-15))
    assert probabilities == [0.0, 1.0, 0.0]
    assert record.probabilities == [0.0, 1.0, 0.0]


def test_precision_and_recall_use_none_for_zero_denominator():
    records = [prediction("UP", "UP", [1.0, 0.0, 0.0])]

    result = evaluate_predictions(BacktestResult(records))

    assert result.precision == [1.0, None, None]
    assert result.recall == [1.0, None, None]


def test_all_skipped_returns_empty_prediction_metrics():
    records = [
        prediction(
            "UP",
            None,
            None,
            STATUS_SKIPPED,
            "ZERO_ROW_UNESTIMATED",
        ),
        prediction(
            "DOWN",
            None,
            None,
            STATUS_SKIPPED,
            "OTHER_REASON",
            1,
        ),
    ]

    result = evaluate_predictions(BacktestResult(records))

    assert result.eligible_count == 2
    assert result.predicted_count == 0
    assert result.skipped_count == 2
    assert result.correct_count == 0
    assert result.coverage == 0.0
    assert result.accuracy is None
    assert result.brier_score is None
    assert result.log_loss is None
    assert result.confusion_matrix == [[0, 0, 0], [0, 0, 0], [0, 0, 0]]


def test_tie_count_counts_maximum_probability_ties():
    records = [
        prediction("UP", "UP", [0.5, 0.5, 0.0]),
        prediction("DOWN", "DOWN", [0.1, 0.2, 0.7], index=1),
        prediction("FLAT", "FLAT", [1 / 3, 1 / 3, 1 / 3], index=2),
    ]

    result = evaluate_predictions(BacktestResult(records))

    assert result.tie_count == 2


@pytest.mark.parametrize(
    "record",
    [
        prediction("UP", "UP", None),
        prediction("UP", "UP", [0.5, 0.5]),
        prediction("UP", "UP", [float("nan"), 0.5, 0.5]),
        prediction("UP", "UP", [-0.1, 0.5, 0.6]),
        prediction("UP", "UP", [0.2, 0.2, 0.2]),
        prediction("UP", None, [1.0, 0.0, 0.0]),
    ],
)
def test_invalid_scored_prediction_is_rejected(record):
    with pytest.raises(ValueError):
        evaluate_predictions(BacktestResult([record]))


def test_invalid_skipped_prediction_is_rejected():
    record = prediction(
        "UP",
        "UP",
        None,
        STATUS_SKIPPED,
        "ZERO_ROW_UNESTIMATED",
    )

    with pytest.raises(ValueError, match="INVALID_SKIPPED_PREDICTED_STATE"):
        evaluate_predictions(BacktestResult([record]))


def test_skipped_prediction_without_reason_is_rejected():
    record = prediction("UP", None, None, STATUS_SKIPPED, None)

    with pytest.raises(ValueError, match="MISSING_SKIP_CODE"):
        evaluate_predictions(BacktestResult([record]))


def test_empty_backtest_result_is_rejected():
    with pytest.raises(ValueError, match="EMPTY_BACKTEST_RESULT"):
        evaluate_predictions(BacktestResult([]))
