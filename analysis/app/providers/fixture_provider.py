import csv
from datetime import date
from decimal import Decimal, InvalidOperation
from pathlib import Path

from app.providers.base import MarketDataProvider, PriceDataset, PricePoint


_REQUIRED_COLUMNS = ["date", "close", "adjusted_close", "volume"]


class FixtureProvider(MarketDataProvider):
    """ローカルの人工CSVだけを読み込むオフラインProvider。"""

    def __init__(self, fixture_path: str | Path):
        self.fixture_path = Path(fixture_path)

    def fetch(
        self,
        ticker: str,
        start_date: date,
        end_date: date,
    ) -> PriceDataset:
        if not ticker:
            raise ValueError("INVALID_TICKER")
        if start_date > end_date:
            raise ValueError("INVALID_DATE_RANGE")

        rows = self._read_rows()
        selected = [
            price
            for price in rows
            if start_date <= price.date <= end_date
        ]
        if not selected:
            raise ValueError("NO_PRICE_DATA")

        return PriceDataset(
            ticker=ticker,
            provider="FIXTURE",
            prices=selected,
        )

    def _read_rows(self) -> list[PricePoint]:
        try:
            with self.fixture_path.open(
                "r",
                encoding="utf-8",
                newline="",
            ) as file:
                reader = csv.DictReader(file)
                if reader.fieldnames != _REQUIRED_COLUMNS:
                    raise ValueError("INVALID_FIXTURE_COLUMNS")
                prices = [self._parse_row(row) for row in reader]
        except FileNotFoundError:
            raise ValueError("FIXTURE_NOT_FOUND") from None
        except OSError as error:
            raise ValueError("FIXTURE_READ_ERROR") from error

        if not prices:
            raise ValueError("NO_PRICE_DATA")

        prices.sort(key=lambda price: price.date)
        if len({price.date for price in prices}) != len(prices):
            raise ValueError("DUPLICATE_FIXTURE_DATE")
        return prices

    @staticmethod
    def _parse_row(row: dict[str, str | None]) -> PricePoint:
        date_value = row.get("date")
        if not date_value:
            raise ValueError("INVALID_FIXTURE_DATE")
        try:
            parsed_date = date.fromisoformat(date_value)
        except ValueError:
            raise ValueError("INVALID_FIXTURE_DATE") from None

        close = _parse_positive_price(row.get("close"))
        adjusted_close = _parse_positive_price(row.get("adjusted_close"))

        volume_value = row.get("volume")
        if volume_value is None or not volume_value.strip():
            volume = None
        else:
            try:
                volume = int(volume_value)
            except ValueError:
                raise ValueError("INVALID_FIXTURE_VOLUME") from None
            if volume < 0:
                raise ValueError("INVALID_FIXTURE_VOLUME")

        return PricePoint(
            date=parsed_date,
            close=close,
            adjusted_close=adjusted_close,
            volume=volume,
        )


def _parse_positive_price(value: str | None) -> str:
    if value is None or not value.strip():
        raise ValueError("INVALID_FIXTURE_PRICE")
    try:
        decimal_value = Decimal(value)
    except (InvalidOperation, ValueError):
        raise ValueError("INVALID_FIXTURE_PRICE") from None
    if not decimal_value.is_finite() or decimal_value <= 0:
        raise ValueError("INVALID_FIXTURE_PRICE")
    return value
