from datetime import date
from pathlib import Path

import pytest

from app.providers.base import PriceDataset, PricePoint
from app.providers.fixture_provider import FixtureProvider


FIXTURE_PATH = Path(__file__).parents[1] / "fixtures" / "synthetic_prices.csv"


def test_fixture_provider_returns_price_dataset():
    result = FixtureProvider(FIXTURE_PATH).fetch(
        "TEST",
        date(2026, 1, 5),
        date(2026, 1, 7),
    )

    assert isinstance(result, PriceDataset)
    assert result.ticker == "TEST"
    assert result.provider == "FIXTURE"
    assert all(isinstance(price, PricePoint) for price in result.prices)


def test_fixture_values_are_parsed_correctly():
    result = FixtureProvider(FIXTURE_PATH).fetch(
        "TEST",
        date(2026, 1, 5),
        date(2026, 1, 7),
    )

    assert [price.date for price in result.prices] == [
        date(2026, 1, 5),
        date(2026, 1, 6),
        date(2026, 1, 7),
    ]
    assert [price.close for price in result.prices] == [
        "100.00",
        "101.00",
        "102.00",
    ]
    assert [price.adjusted_close for price in result.prices] == [
        "99.50",
        "100.50",
        "101.50",
    ]
    assert [price.volume for price in result.prices] == [1000, None, 1200]


def test_fixture_provider_does_not_use_network(monkeypatch):
    def fail_if_called(*args, **kwargs):
        raise AssertionError("network must not be called")

    monkeypatch.setattr("urllib.request.urlopen", fail_if_called)

    result = FixtureProvider(FIXTURE_PATH).fetch(
        "TEST",
        date(2026, 1, 5),
        date(2026, 1, 7),
    )

    assert len(result.prices) == 3


@pytest.mark.parametrize(
    ("content", "error_code"),
    [
        ("day,close,adjusted_close,volume\n2026-01-05,100,99,1\n", "INVALID_FIXTURE_COLUMNS"),
        ("date,close,adjusted_close,volume\nnot-a-date,100,99,1\n", "INVALID_FIXTURE_DATE"),
        ("date,close,adjusted_close,volume\n2026-01-05,0,99,1\n", "INVALID_FIXTURE_PRICE"),
        ("date,close,adjusted_close,volume\n2026-01-05,100,99,-1\n", "INVALID_FIXTURE_VOLUME"),
        (
            "date,close,adjusted_close,volume\n"
            "2026-01-05,100,99,1\n"
            "2026-01-05,101,100,2\n",
            "DUPLICATE_FIXTURE_DATE",
        ),
    ],
)
def test_broken_fixture_is_rejected(tmp_path, content, error_code):
    fixture_path = tmp_path / "broken.csv"
    fixture_path.write_text(content, encoding="utf-8")

    with pytest.raises(ValueError, match=error_code):
        FixtureProvider(fixture_path).fetch(
            "TEST",
            date(2026, 1, 5),
            date(2026, 1, 7),
        )


def test_missing_fixture_is_rejected(tmp_path):
    with pytest.raises(ValueError, match="FIXTURE_NOT_FOUND"):
        FixtureProvider(tmp_path / "missing.csv").fetch(
            "TEST",
            date(2026, 1, 5),
            date(2026, 1, 7),
        )
