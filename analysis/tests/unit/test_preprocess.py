from datetime import date

import pytest

from app.data import preprocess
from app.data.preprocess import (
    extract_adjusted_closes,
    preprocess_price_dataset,
)
from app.providers.base import PriceDataset, PricePoint


@pytest.fixture
def trading_dates(monkeypatch):
    monkeypatch.setattr(
        preprocess,
        "sessions",
        lambda start, end: [
            date(2026, 1, 5),
            date(2026, 1, 6),
            date(2026, 1, 7),
        ],
    )


def point(day, close="100.0", adjusted_close="99.0", volume=100):
    return PricePoint(
        date=day,
        close=close,
        adjusted_close=adjusted_close,
        volume=volume,
    )


def dataset(prices):
    return PriceDataset("TEST", "TEST_PROVIDER", prices)


def test_normal_data_is_accepted_and_adjusted_close_is_extractable(trading_dates):
    prices = [
        point(date(2026, 1, 5), "100.0", "99.0"),
        point(date(2026, 1, 6), "101.0", "100.0", volume=None),
        point(date(2026, 1, 7), "102.0", "101.0"),
    ]

    result = preprocess_price_dataset(dataset(prices))

    assert result.prices == prices
    assert extract_adjusted_closes(dataset(prices)) == ["99.0", "100.0", "101.0"]


def test_reverse_order_is_normalized(trading_dates):
    prices = [
        point(date(2026, 1, 7)),
        point(date(2026, 1, 6)),
        point(date(2026, 1, 5)),
    ]

    result = preprocess_price_dataset(dataset(prices))

    assert [price.date for price in result.prices] == [
        date(2026, 1, 5),
        date(2026, 1, 6),
        date(2026, 1, 7),
    ]


def test_identical_duplicate_is_collapsed(trading_dates):
    duplicate = point(date(2026, 1, 6))
    prices = [
        point(date(2026, 1, 5)),
        duplicate,
        duplicate,
        point(date(2026, 1, 7)),
    ]

    result = preprocess_price_dataset(dataset(prices))

    assert len(result.prices) == 3
    assert result.prices[1] == duplicate


def test_different_price_on_same_date_is_rejected(trading_dates):
    prices = [
        point(date(2026, 1, 5)),
        point(date(2026, 1, 6), close="101.0"),
        point(date(2026, 1, 6), close="102.0"),
        point(date(2026, 1, 7)),
    ]

    with pytest.raises(ValueError, match="DUPLICATE_DATE_CONFLICT"):
        preprocess_price_dataset(dataset(prices))


@pytest.mark.parametrize("close", ["0", "-1", "NaN"])
def test_non_positive_or_non_finite_close_is_rejected(trading_dates, close):
    prices = [
        point(date(2026, 1, 5), close=close),
        point(date(2026, 1, 6)),
        point(date(2026, 1, 7)),
    ]

    with pytest.raises(ValueError, match="PRICE_VALUE"):
        preprocess_price_dataset(dataset(prices))


def test_missing_price_is_rejected(trading_dates):
    prices = [
        point(date(2026, 1, 5), close=None),
        point(date(2026, 1, 6)),
        point(date(2026, 1, 7)),
    ]

    with pytest.raises(ValueError, match="MISSING_PRICE_VALUE"):
        preprocess_price_dataset(dataset(prices))


def test_missing_trading_day_is_data_gap(monkeypatch):
    monkeypatch.setattr(
        preprocess,
        "sessions",
        lambda start, end: [
            date(2026, 1, 5),
            date(2026, 1, 6),
            date(2026, 1, 7),
        ],
    )
    prices = [
        point(date(2026, 1, 5)),
        point(date(2026, 1, 7)),
    ]

    with pytest.raises(ValueError, match="DATA_GAP"):
        preprocess_price_dataset(dataset(prices))
