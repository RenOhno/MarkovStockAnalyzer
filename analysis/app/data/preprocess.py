from datetime import date
from decimal import Decimal, InvalidOperation

from app.data.calendar import sessions
from app.providers.base import PriceDataset, PricePoint


_PRICE_FIELDS = ("close", "adjusted_close")


def preprocess_price_dataset(dataset: PriceDataset) -> PriceDataset:
    """株価系列を分析へ渡せる状態へ正規化・検証する。

    欠損値の補完は行わず、入力データの日付範囲にあるXTKSの取引日が
    すべて存在することを確認する。
    """
    if not dataset.prices:
        raise ValueError("NO_PRICE_DATA")

    sorted_prices = sorted(dataset.prices, key=lambda price: price.date)
    unique_prices: list[PricePoint] = []

    for price in sorted_prices:
        _validate_price_values(price)

        if unique_prices and unique_prices[-1].date == price.date:
            previous = unique_prices[-1]
            if price == previous:
                continue
            if (
                price.close != previous.close
                or price.adjusted_close != previous.adjusted_close
            ):
                raise ValueError("DUPLICATE_DATE_CONFLICT")
            raise ValueError("DUPLICATE_DATE_CONFLICT")

        unique_prices.append(price)

    expected_dates = set(
        sessions(unique_prices[0].date, unique_prices[-1].date)
    )
    actual_dates = {price.date for price in unique_prices}

    missing_dates = expected_dates - actual_dates
    if missing_dates:
        raise ValueError("DATA_GAP")

    unexpected_dates = actual_dates - expected_dates
    if unexpected_dates:
        raise ValueError("INVALID_TRADING_DATE")

    return PriceDataset(
        ticker=dataset.ticker,
        provider=dataset.provider,
        prices=unique_prices,
    )


def extract_adjusted_closes(dataset: PriceDataset) -> list[str]:
    """検証済みの日付順Adjusted Closeを取り出す。"""
    normalized = preprocess_price_dataset(dataset)
    return [price.adjusted_close for price in normalized.prices]


def _validate_price_values(price: PricePoint) -> None:
    for field_name in _PRICE_FIELDS:
        value = getattr(price, field_name)
        try:
            decimal_value = Decimal(str(value))
        except (InvalidOperation, ValueError, TypeError):
            raise ValueError("MISSING_PRICE_VALUE") from None

        if not decimal_value.is_finite():
            raise ValueError("INVALID_PRICE_VALUE")
        if decimal_value <= 0:
            raise ValueError("INVALID_PRICE_VALUE")
