from datetime import datetime, timezone

from fastapi import APIRouter, Depends, Request

from app.api.security import APIError, require_internal_token
from app.data.calendar import previous_session
from app.data.payload import (
    ADJUSTMENT_POLICY,
    EXCHANGE,
    PRICE_BASIS,
    TIME_ZONE,
    YFINANCE_FETCH_OPTIONS,
    calendar_version,
    content_sha256,
    metadata,
    normalize_price_dataset,
    provider_version,
)
from app.data.preprocess import preprocess_price_dataset
from app.providers.base import MarketDataProvider
from app.schemas import (
    ENGINE_VERSION,
    FetchPricesRequest,
    HealthResponse,
    PriceDatasetMetadata,
    PriceDatasetPayload,
    PricePointPayload,
)


router = APIRouter(
    prefix="/internal/v1",
    dependencies=[Depends(require_internal_token)],
)


@router.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(
        status="UP",
        engineVersion=ENGINE_VERSION,
    )


def get_market_data_provider(request: Request) -> MarketDataProvider:
    return request.app.state.provider_factory()


def _provider_error(error: Exception) -> APIError:
    code = str(error)
    if code == "NO_PRICE_DATA":
        return APIError("NO_PRICE_DATA", "No price data was returned", 422)
    if code in {
        "MISSING_PRICE_VALUE",
        "INVALID_PRICE_VALUE",
        "INVALID_VOLUME_VALUE",
    }:
        return APIError("INVALID_PRICE_DATA", "Price data is invalid", 422)
    return APIError("PROVIDER_UNAVAILABLE", "Price provider unavailable", 502)


def _preprocess_error(error: ValueError) -> APIError:
    code = str(error)
    if code == "DATA_GAP":
        return APIError("DATA_GAP", "Trading day data is missing", 422)
    if code in {
        "MISSING_PRICE_VALUE",
        "INVALID_PRICE_VALUE",
        "INVALID_VOLUME_VALUE",
    }:
        return APIError("INVALID_PRICE_DATA", "Price data is invalid", 422)
    if code == "NO_PRICE_DATA":
        return APIError("NO_PRICE_DATA", "No price data was returned", 422)
    return APIError("INVALID_PRICE_DATA", "Price data is invalid", 422)


@router.post(
    "/prices/fetch",
    response_model=PriceDatasetPayload,
)
def fetch_prices(
    payload: FetchPricesRequest,
    provider: MarketDataProvider = Depends(get_market_data_provider),
) -> PriceDatasetPayload:
    fetch_start = previous_session(payload.startDate)
    try:
        dataset = provider.fetch(
            ticker=payload.ticker,
            start_date=fetch_start,
            end_date=payload.endDate,
        )
    except TimeoutError as error:
        raise APIError(
            "PROVIDER_TIMEOUT",
            "Price provider timed out",
            504,
        ) from error
    except (ConnectionError, OSError) as error:
        raise APIError(
            "PROVIDER_UNAVAILABLE",
            "Price provider unavailable",
            502,
        ) from error
    except ValueError as error:
        raise _provider_error(error) from error
    except Exception as error:
        raise APIError(
            "PROVIDER_UNAVAILABLE",
            "Price provider unavailable",
            502,
        ) from error

    try:
        normalized = normalize_price_dataset(
            preprocess_price_dataset(dataset)
        )
    except ValueError as error:
        raise _preprocess_error(error) from error

    provider_version_value = provider_version(normalized.provider)
    calendar_version_value = calendar_version()
    fetched_at = datetime.now(timezone.utc)
    metadata_value = metadata(
        normalized,
        YFINANCE_FETCH_OPTIONS,
        provider_version_value,
        calendar_version_value,
    )
    prices = [
        PricePointPayload(
            date=price.date,
            close=price.close,
            adjustedClose=price.adjusted_close,
            volume=price.volume,
        )
        for price in normalized.prices
    ]
    return PriceDatasetPayload(
        ticker=normalized.ticker,
        exchange=EXCHANGE,
        timeZone=TIME_ZONE,
        priceBasis=PRICE_BASIS,
        provider=normalized.provider,
        providerVersion=provider_version_value,
        adjustmentPolicy=ADJUSTMENT_POLICY,
        fetchedAt=fetched_at,
        coverageStart=prices[0].date,
        coverageEnd=prices[-1].date,
        contentSha256=content_sha256(
            normalized,
            provider_version_value,
        ),
        metadata=PriceDatasetMetadata(**metadata_value),
        prices=prices,
    )
