import numpy as np
import pytest

from app.core.baselines import (
    predict_majority_state,
    predict_persistence_state,
)


def test_majority_uses_training_states_only():
    training_states = [0, 1, 1, 2, 1]

    assert predict_majority_state(training_states) == 1


def test_majority_does_not_use_target_or_future_states():
    training_states = np.array([0, 0, 1])
    original_prediction = predict_majority_state(training_states)

    assert original_prediction == 0
    assert predict_majority_state(np.append(training_states, [2, 2, 2])) == 2
    assert predict_majority_state(training_states) == original_prediction


def test_majority_returns_each_possible_state():
    assert predict_majority_state([0, 0, 1]) == 0
    assert predict_majority_state([1, 2, 1]) == 1
    assert predict_majority_state([2, 0, 2]) == 2


def test_majority_tie_uses_up_flat_down_order():
    assert predict_majority_state([2, 1, 0]) == 0
    assert predict_majority_state([2, 1, 0, 1]) == 1


def test_persistence_returns_current_state_without_target():
    assert predict_persistence_state(0) == 0
    assert predict_persistence_state(1) == 1
    assert predict_persistence_state(2) == 2


@pytest.mark.parametrize("state", [-1, 3, "UP"])
def test_baselines_reject_invalid_states(state):
    with pytest.raises(ValueError):
        predict_persistence_state(state)
