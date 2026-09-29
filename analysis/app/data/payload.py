import hashlib
from datetime import date
from decimal import Decimal, ROUND_HALF_UP
from importlib.metadata import version

from app.providers.base import PriceDataset, PricePoint


EXCHANGE = "XTKS"
TIME_ZONE = "Asia/Tokyo"
PRICE_BASIS = "PROVIDER_ADJUSTED_CLOSE"
ADJUSTMENT_POLICY = "PROVIDER_ADJUSTED_CLOSE_V1"
NORMALIZATION_VERSION = "NORMALIZATION_V1"
ROUNDING_MODE = "ROUND_HALF_UP"
PRICE_SCALE = 10
_SCHEMA_VERSION = 1
_QUANTUM = Decimal("0.0000000001")
YFINANCE_FETCH_OPTIONS = {
    "interval": "1d",
    "auto_adjust": False,
    "actions": True,
    "repair": False,
    "rounding": False,
}


def normalize_price_dataset(dataset: PriceDataset) -> PriceDataset:
    return PriceDataset(
        ticker=dataset.ticker,
        provider=dataset.provider,
        prices=[
            PricePoint(
                date=price.date,
                close=_normalize_price(price.close),
                adjusted_close=_normalize_price(price.adjusted_close),
                volume=_normalize_volume(price.volume),
            )
            for price in dataset.prices
        ],
    )


def canonical_data_bytes(
    dataset: PriceDataset,
    provider_version: str,
) -> bytes:
    prices = sorted(dataset.prices, key=lambda price: price.date)
    header_values = [
        ("schemaVersion", str(_SCHEMA_VERSION)),
        ("normalizationVersion", NORMALIZATION_VERSION),
        ("ticker", dataset.ticker),
        ("exchange", EXCHANGE),
        ("provider", dataset.provider),
        ("providerVersion", provider_version),
        ("adjustmentPolicy", ADJUSTMENT_POLICY),
    ]
    for _, value in header_values:
        if any(character in value for character in ("\r", "\n", ",")):
            raise ValueError("INVALID_CANONICAL_HEADER")

    lines = [f"{name}={value}" for name, value in header_values]
    lines.append("date,close,adjustedClose,volume")
    lines.extend(
        ",".join(
            [
                price.date.isoformat(),
                _normalize_price(price.close),
                _normalize_price(price.adjusted_close),
                "NULL" if price.volume is None else str(price.volume),
            ]
        )
        for price in prices
    )
    return ("\n".join(lines) + "\n").encode("utf-8")


def content_sha256(dataset: PriceDataset, provider_version: str) -> str:
    return hashlib.sha256(
        canonical_data_bytes(dataset, provider_version)
    ).hexdigest()


def provider_version(provider: str) -> str:
    if provider == "YFINANCE":
        return version("yfinance")
    return provider


def calendar_version() -> str:
    return version("exchange-calendars")


def metadata(
    dataset: PriceDataset,
    fetch_options: dict[str, object],
    provider_version_value: str,
    calendar_version_value: str,
) -> dict[str, object]:
    return {
        "schemaVersion": _SCHEMA_VERSION,
        "normalizationVersion": NORMALIZATION_VERSION,
        "calendarName": EXCHANGE,
        "calendarVersion": calendar_version_value,
        "roundingMode": ROUNDING_MODE,
        "scale": PRICE_SCALE,
        "fetchOptions": fetch_options,
        "qualityFlags": [],
        "ticker": dataset.ticker,
        "exchange": EXCHANGE,
        "timeZone": TIME_ZONE,
        "priceBasis": PRICE_BASIS,
        "provider": dataset.provider,
        "providerVersion": provider_version_value,
    }


def _normalize_price(value: str) -> str:
    decimal_value = Decimal(str(value))
    normalized = decimal_value.quantize(
        _QUANTUM,
        rounding=ROUND_HALF_UP,
    )
    return f"{normalized:.{PRICE_SCALE}f}"


def _normalize_volume(value: int | None) -> int | None:
    if value is None:
        return None
    if isinstance(value, bool) or not isinstance(value, int) or value < 0:
        raise ValueError("INVALID_VOLUME_VALUE")
    return value
