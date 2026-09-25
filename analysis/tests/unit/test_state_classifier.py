from decimal import Decimal

import numpy as np
import pytest

from app.core.state_classifier import classify_returns


def test_classify_returns():
    returns = [
        Decimal("0.01"),     # +1.0%
        Decimal("0.003"),    # +0.3%
        Decimal("-0.01"),    # -1.0%
    ]

    result = classify_returns(returns)

    expected = np.array([0, 1, 2])

    np.testing.assert_array_equal(result, expected)


def test_boundary_values_are_flat():
    returns = [
        Decimal("0.005"),
        Decimal("-0.005"),
    ]

    result = classify_returns(returns)

    expected = np.array([1, 1])

    np.testing.assert_array_equal(result, expected)


def test_above_upper_is_up():
    result = classify_returns([Decimal("0.0051")])

    assert result[0] == 0


def test_below_lower_is_down():
    result = classify_returns([Decimal("-0.0051")])

    assert result[0] == 2


def test_invalid_thresholds():
    with pytest.raises(ValueError, match="INVALID_THRESHOLDS"):
        classify_returns(
            [Decimal("0.01")],
            lower="0.01",
            upper="0.005",
        )