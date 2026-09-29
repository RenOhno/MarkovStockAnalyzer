from datetime import date, timedelta

import numpy as np
import pytest

from app.core import backtest
from app.core.backtest import (
    SKIP_ZERO_ROW_UNESTIMATED,
    STATUS_SCORED,
    STATUS_SKIPPED,
    walk_forward,
)


def make_series(states):
    start = date(2026, 1, 1)
    dates = [start + timedelta(days=index) for index in range(len(states))]
    return states, dates


def test_expanding_walk_forward_scores_one_day_ahead():
    states, dates = make_series([0, 1, 2] * 14)

    result = walk_forward(
        states,
        dates,
        dates[30],
        dates[34],
    )

    assert len(result.predictions) == 5
    assert all(record.status == STATUS_SCORED for record in result.predictions)
    assert all(record.predicted_state is not None for record in result.predictions)
    assert all(record.probabilities is not None for record in result.predictions)
    assert all(record.skip_code is None for record in result.predictions)
    assert all(
        np.isclose(sum(record.probabilities), 1.0)
        for record in result.predictions
    )


def test_training_slice_ends_at_origin_and_start_stays_fixed(monkeypatch):
    states, dates = make_series([0, 1, 2] * 14)
    observed_training_lengths = []
    original_count = backtest.count_transitions

    def record_training_length(training_states):
        observed_training_lengths.append(len(training_states))
        return original_count(training_states)

    monkeypatch.setattr(backtest, "count_transitions", record_training_length)

    result = walk_forward(states, dates, dates[30], dates[34])

    assert observed_training_lengths == [30, 31, 32, 33, 34]
    assert [record.train_start for record in result.predictions] == [dates[0]] * 5
    assert [record.train_end for record in result.predictions] == dates[29:34]
    assert [record.origin_date for record in result.predictions] == dates[29:34]
    assert [record.target_date for record in result.predictions] == dates[30:35]


def test_future_target_is_not_in_training_slice(monkeypatch):
    states, dates = make_series([0, 1, 2] * 14)
    observed_training_slices = []
    original_count = backtest.count_transitions

    def record_training_slice(training_states):
        observed_training_slices.append(list(training_states))
        return original_count(training_states)

    monkeypatch.setattr(backtest, "count_transitions", record_training_slice)

    walk_forward(states, dates, dates[30], dates[32])

    assert observed_training_slices == [
        states[:30],
        states[:31],
        states[:32],
    ]


@pytest.mark.parametrize(
    ("min_train_states", "error_code"),
    [(29, "INVALID_MIN_TRAIN_STATES"), (31, "INSUFFICIENT_TRAINING_STATES")],
)
def test_training_state_constraints_are_rejected(min_train_states, error_code):
    states, dates = make_series([0, 1, 2] * 14)

    with pytest.raises(ValueError, match=error_code):
        walk_forward(
            states,
            dates,
            dates[30],
            dates[30],
            min_train_states=min_train_states,
        )


def test_only_one_day_horizon_is_supported():
    states, dates = make_series([0, 1, 2] * 14)

    with pytest.raises(ValueError, match="INVALID_HORIZON"):
        walk_forward(states, dates, dates[30], dates[30], horizon=3)


def test_unestimated_training_row_is_skipped_without_probabilities():
    states, dates = make_series([0] * 35)

    result = walk_forward(states, dates, dates[30], dates[30])
    record = result.predictions[0]

    assert record.status == STATUS_SKIPPED
    assert record.skip_code == SKIP_ZERO_ROW_UNESTIMATED
    assert record.predicted_state is None
    assert record.probabilities is None
    assert record.actual_state == "UP"


def test_predicted_state_is_the_highest_probability_state():
    states, dates = make_series([0, 1, 2] * 14)

    result = walk_forward(states, dates, dates[30], dates[30])
    record = result.predictions[0]

    assert record.probabilities is not None
    predicted_index = ("UP", "FLAT", "DOWN").index(record.predicted_state)
    assert predicted_index == int(np.argmax(record.probabilities))
    assert record.probabilities[predicted_index] == max(record.probabilities)


def test_tied_probability_uses_up_flat_down_order(monkeypatch):
    states, dates = make_series([0, 1, 2] * 14)
    monkeypatch.setattr(
        backtest,
        "forecast_distribution",
        lambda matrix, current_state, horizon: np.array([0.5, 0.5, 0.0]),
    )

    result = walk_forward(states, dates, dates[30], dates[30])

    assert result.predictions[0].predicted_state == "UP"
