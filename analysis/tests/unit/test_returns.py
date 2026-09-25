from decimal import Decimal, localcontext

import pytest

from app.core.returns import calculate_returns


def test_calculate_returns():
    prices = ["100", "101", "99"]

    result = calculate_returns(prices)

    assert result[0] == Decimal("0.01")

    with localcontext() as context:
        context.prec = 50
        expected = (Decimal("99") - Decimal("101")) / Decimal("101")

    assert result[1] == expected


def test_same_price_returns_zero():
    prices = ["100", "100"]

    result = calculate_returns(prices)

    assert result == [Decimal("0")]


def test_requires_at_least_two_prices():
    with pytest.raises(ValueError, match="INVALID_PRICES"):
        calculate_returns(["100"])


def test_rejects_zero_price():
    with pytest.raises(ValueError, match="INVALID_PRICES"):
        calculate_returns(["100", "0"])


def test_rejects_negative_price():
    with pytest.raises(ValueError, match="INVALID_PRICES"):
        calculate_returns(["100", "-1"])