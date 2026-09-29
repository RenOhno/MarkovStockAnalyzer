from dataclasses import dataclass
from datetime import date
from typing import Sequence

import numpy as np

from app.core.baselines import (
    predict_majority_state,
    predict_persistence_state,
)
from app.core.markov import forecast_distribution
from app.core.transition import count_transitions, estimate_mle


STATE_ORDER = ("UP", "FLAT", "DOWN")
TRAINING_MODE_EXPANDING = "EXPANDING"
STATUS_SCORED = "SCORED"
STATUS_SKIPPED = "SKIPPED"
SKIP_ZERO_ROW_UNESTIMATED = "ZERO_ROW_UNESTIMATED"


@dataclass(frozen=True)
class BacktestPrediction:
    origin_date: date
    target_date: date
    train_start: date
    train_end: date
    actual_state: str
    predicted_state: str | None
    probabilities: list[float] | None
    status: str
    skip_code: str | None
    majority_state: str | None = None
    persistence_state: str | None = None


@dataclass(frozen=True)
class BacktestResult:
    predictions: list[BacktestPrediction]


def walk_forward(
    states: Sequence[int],
    dates: Sequence[date],
    test_start: date,
    test_end: date,
    min_train_states: int = 30,
    training_mode: str = TRAINING_MODE_EXPANDING,
    horizon: int = 1,
) -> BacktestResult:
    """拡大型学習で1営業日先の状態を予測する。"""
    state_array = np.asarray(states)
    date_list = list(dates)

    _validate_inputs(
        state_array,
        date_list,
        test_start,
        test_end,
        min_train_states,
        training_mode,
        horizon,
    )

    evaluation_indices = [
        index
        for index, target_date in enumerate(date_list)
        if test_start <= target_date <= test_end
    ]
    if not evaluation_indices or evaluation_indices[0] == 0:
        raise ValueError("INVALID_EVALUATION_RANGE")

    first_origin_index = evaluation_indices[0] - 1
    if first_origin_index + 1 < min_train_states:
        raise ValueError("INSUFFICIENT_TRAINING_STATES")

    predictions = []
    for target_index in evaluation_indices:
        origin_index = target_index - 1
        training_states = state_array[: origin_index + 1]
        counts = count_transitions(training_states)
        matrix, unestimated_rows = estimate_mle(counts)

        origin_date = date_list[origin_index]
        target_date = date_list[target_index]
        train_start = date_list[0]
        train_end = origin_date

        if len(unestimated_rows) > 0:
            predictions.append(
                BacktestPrediction(
                    origin_date=origin_date,
                    target_date=target_date,
                    train_start=train_start,
                    train_end=train_end,
                    actual_state=_state_name(state_array[target_index]),
                    predicted_state=None,
                    probabilities=None,
                    status=STATUS_SKIPPED,
                    skip_code=SKIP_ZERO_ROW_UNESTIMATED,
                    majority_state=None,
                    persistence_state=None,
                )
            )
            continue

        probabilities = forecast_distribution(
            matrix,
            int(state_array[origin_index]),
            horizon,
        )
        predicted_state = int(np.argmax(probabilities))
        majority_state = predict_majority_state(training_states)
        persistence_state = predict_persistence_state(
            int(state_array[origin_index])
        )
        predictions.append(
            BacktestPrediction(
                origin_date=origin_date,
                target_date=target_date,
                train_start=train_start,
                train_end=train_end,
                actual_state=_state_name(state_array[target_index]),
                predicted_state=_state_name(predicted_state),
                probabilities=probabilities.tolist(),
                status=STATUS_SCORED,
                skip_code=None,
                majority_state=_state_name(majority_state),
                persistence_state=_state_name(persistence_state),
            )
        )

    return BacktestResult(predictions=predictions)


def _validate_inputs(
    states: np.ndarray,
    dates: list[date],
    test_start: date,
    test_end: date,
    min_train_states: int,
    training_mode: str,
    horizon: int,
) -> None:
    if states.ndim != 1 or len(states) != len(dates):
        raise ValueError("INVALID_STATE_DATE_SERIES")
    if len(states) < 2 or not np.issubdtype(states.dtype, np.integer):
        raise ValueError("INVALID_STATES")
    if np.any((states < 0) | (states >= len(STATE_ORDER))):
        raise ValueError("INVALID_STATE")
    if any(left >= right for left, right in zip(dates, dates[1:])):
        raise ValueError("INVALID_DATE_SERIES")
    if test_start > test_end:
        raise ValueError("INVALID_DATE_RANGE")
    if min_train_states < 30:
        raise ValueError("INVALID_MIN_TRAIN_STATES")
    if training_mode != TRAINING_MODE_EXPANDING:
        raise ValueError("UNSUPPORTED_TRAINING_MODE")
    if horizon != 1:
        raise ValueError("INVALID_HORIZON")


def _state_name(state: int) -> str:
    return STATE_ORDER[state]
