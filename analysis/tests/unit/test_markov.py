import numpy as np
import pytest

from app.core.markov import forecast_distribution


def test_one_step_forecast():
    matrix = np.array([
        [0.5, 0.3, 0.2],
        [0.4, 0.2, 0.4],
        [0.3, 0.2, 0.5],
    ])

    result = forecast_distribution(
        matrix,
        current_state=0,
        horizon=1,
    )

    expected = np.array([0.5, 0.3, 0.2])

    np.testing.assert_allclose(result, expected)


def test_zero_step_returns_current_state():
    matrix = np.array([
        [0.5, 0.3, 0.2],
        [0.4, 0.2, 0.4],
        [0.3, 0.2, 0.5],
    ])

    result = forecast_distribution(
        matrix,
        current_state=1,
        horizon=0,
    )

    expected = np.array([0.0, 1.0, 0.0])

    np.testing.assert_allclose(result, expected)


def test_distribution_sums_to_one():
    matrix = np.array([
        [0.5, 0.3, 0.2],
        [0.4, 0.2, 0.4],
        [0.3, 0.2, 0.5],
    ])

    result = forecast_distribution(
        matrix,
        current_state=2,
        horizon=10,
    )

    assert np.isclose(result.sum(), 1.0)


def test_unestimated_matrix_is_rejected():
    matrix = np.array([
        [0.5, 0.5, 0.0],
        [np.nan, np.nan, np.nan],
        [0.4, 0.2, 0.4],
    ])

    with pytest.raises(
        ValueError,
        match="UNESTIMATED_MATRIX",
    ):
        forecast_distribution(
            matrix,
            current_state=0,
            horizon=1,
        )


def test_invalid_current_state():
    matrix = np.array([
        [0.5, 0.3, 0.2],
        [0.4, 0.2, 0.4],
        [0.3, 0.2, 0.5],
    ])

    with pytest.raises(
        ValueError,
        match="INVALID_CURRENT_STATE",
    ):
        forecast_distribution(
            matrix,
            current_state=3,
            horizon=1,
        )


def test_invalid_horizon():
    matrix = np.array([
        [0.5, 0.3, 0.2],
        [0.4, 0.2, 0.4],
        [0.3, 0.2, 0.5],
    ])

    with pytest.raises(
        ValueError,
        match="INVALID_HORIZON",
    ):
        forecast_distribution(
            matrix,
            current_state=0,
            horizon=-1,
        )