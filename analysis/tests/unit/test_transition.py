import numpy as np
import pytest

from app.core.transition import (
    count_transitions,
    estimate_mle,
)


def test_count_transitions():
    states = np.array([0, 0, 1, 2, 0])

    result = count_transitions(states)

    expected = np.array([
        [1, 1, 0],
        [0, 0, 1],
        [1, 0, 0],
    ])

    np.testing.assert_array_equal(result, expected)


def test_transition_count_is_n_minus_one():
    states = np.array([0, 1, 2, 0, 1])

    result = count_transitions(states)

    assert result.sum() == len(states) - 1


def test_estimate_mle():
    counts = np.array([
        [1, 1, 0],
        [0, 0, 1],
        [1, 0, 0],
    ])

    matrix, unestimated_rows = estimate_mle(counts)

    expected = np.array([
        [0.5, 0.5, 0.0],
        [0.0, 0.0, 1.0],
        [1.0, 0.0, 0.0],
    ])

    np.testing.assert_allclose(matrix, expected)

    assert len(unestimated_rows) == 0


def test_unestimated_row():
    counts = np.array([
        [1, 1, 0],
        [0, 0, 0],
        [1, 0, 0],
    ])

    matrix, unestimated_rows = estimate_mle(counts)

    assert np.isnan(matrix[1]).all()

    np.testing.assert_array_equal(
        unestimated_rows,
        np.array([1]),
    )


def test_invalid_state():
    states = np.array([0, 1, 3])

    with pytest.raises(
        ValueError,
        match="INVALID_STATE",
    ):
        count_transitions(states)