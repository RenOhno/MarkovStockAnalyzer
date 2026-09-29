from datetime import datetime, timezone

from fastapi import APIRouter, Depends, Request

from app.api.analysis_adapter import (
    build_analysis_response,
    dataset_from_payload,
    validate_condition_dataset,
    validate_preprocessed_dataset,
)
from app.api.backtest_adapter import (
    MAX_EVALUATION_CANDIDATES,
    build_backtest_response,
    evaluation_dates,
    prepare_backtest_dataset,
)
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
from app.core.backtest import walk_forward
from app.core.metrics import evaluate_predictions
from app.providers.base import MarketDataProvider
from app.services.analysis_service import AnalysisService
from app.schemas import (
    AnalyzeInput,
    BacktestInput,
    CalculatedBacktest,
    CalculatedAnalysis,
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


def _analysis_error(error: ValueError) -> APIError:
    code = str(error)
    if code == "ENGINE_VERSION_UNSUPPORTED":
        return APIError(code, "Engine version is not supported", 409)
    if code in {"DATASET_HASH_MISMATCH", "DATASET_CONDITION_MISMATCH"}:
        return APIError(
            code,
            "Dataset does not match the analysis condition",
            409,
        )
    if code == "INSUFFICIENT_STATES":
        return APIError(
            code,
            "Analysis requires at least 30 states",
            422,
        )
    if code in {"DATA_GAP", "INVALID_PRICE_DATA", "INVALID_DATASET"}:
        return APIError(code, "Dataset is invalid", 422)
    if code in {"INVALID_CONDITION", "INVALID_ANALYSIS_RANGE"}:
        return APIError(code, "Analysis condition is invalid", 422)
    if code == "CALCULATION_INVARIANT_FAILED":
        return APIError(code, "Calculation invariant failed", 500)
    return APIError("CALCULATION_INVARIANT_FAILED", "Calculation failed", 500)


@router.post(
    "/analyze",
    response_model=CalculatedAnalysis,
)
def analyze(payload: AnalyzeInput) -> CalculatedAnalysis:
    if payload.engineVersion != ENGINE_VERSION:
        raise APIError(
            "ENGINE_VERSION_UNSUPPORTED",
            "Engine version is not supported",
            409,
        )

    try:
        dataset = dataset_from_payload(payload.dataset)
        validate_condition_dataset(payload.condition, dataset)
        dataset = validate_preprocessed_dataset(dataset)
        result = AnalysisService().analyze(
            dataset,
            payload.condition.startDate,
            payload.condition.endDate,
            str(payload.condition.lowerThreshold),
            str(payload.condition.upperThreshold),
        )
        return build_analysis_response(
            result,
            payload.dataset.contentSha256,
        )
    except ValueError as error:
        raise _analysis_error(error) from error
    except Exception as error:
        raise APIError(
            "CALCULATION_INVARIANT_FAILED",
            "Calculation failed",
            500,
        ) from error


def _backtest_error(error: ValueError) -> APIError:
    code = str(error)
    if code == "INSUFFICIENT_TRAINING_STATES":
        return APIError(
            "INSUFFICIENT_TRAINING_DATA",
            "Not enough training states before the first target",
            422,
        )
    if code in {
        "INVALID_EVALUATION_RANGE",
        "EVALUATION_RANGE_INVALID",
    }:
        return APIError(code, "Evaluation range is invalid", 422)
    if code == "EVALUATION_CANDIDATE_LIMIT_EXCEEDED":
        return APIError(code, "Too many evaluation candidates", 422)
    if code in {
        "DATASET_HASH_MISMATCH",
        "DATASET_CONDITION_MISMATCH",
    }:
        return APIError(
            code,
            "Dataset does not match the backtest condition",
            409,
        )
    if code in {"DATA_GAP", "INVALID_PRICE_DATA", "INVALID_DATASET"}:
        return APIError(code, "Dataset is invalid", 422)
    if code == "CALCULATION_INVARIANT_FAILED":
        return APIError(code, "Calculation invariant failed", 500)
    return APIError("CALCULATION_INVARIANT_FAILED", "Calculation failed", 500)


@router.post(
    "/backtest",
    response_model=CalculatedBacktest,
)
def backtest(payload: BacktestInput) -> CalculatedBacktest:
    if payload.engineVersion != ENGINE_VERSION:
        raise APIError(
            "ENGINE_VERSION_UNSUPPORTED",
            "Engine version is not supported",
            409,
        )
    if not (
        payload.condition.startDate
        <= payload.evaluation.testStart
        <= payload.evaluation.testEnd
        <= payload.condition.endDate
    ):
        raise APIError(
            "EVALUATION_RANGE_INVALID",
            "Evaluation range must be inside the condition range",
            422,
        )

    try:
        _, states, state_dates = prepare_backtest_dataset(
            payload.condition,
            payload.dataset,
        )
        candidates = evaluation_dates(state_dates, payload.evaluation)
        if not candidates:
            raise ValueError("INVALID_EVALUATION_RANGE")
        if len(candidates) > MAX_EVALUATION_CANDIDATES:
            raise ValueError("EVALUATION_CANDIDATE_LIMIT_EXCEEDED")

        result = walk_forward(
            states,
            state_dates,
            payload.evaluation.testStart,
            payload.evaluation.testEnd,
            min_train_states=payload.evaluation.minTrainStates,
            training_mode=payload.evaluation.trainingMode,
            horizon=payload.evaluation.horizon,
        )
        metrics = evaluate_predictions(result)
        return build_backtest_response(
            result,
            metrics,
            payload.evaluation,
            payload.dataset.contentSha256,
        )
    except ValueError as error:
        raise _backtest_error(error) from error
    except Exception as error:
        raise APIError(
            "CALCULATION_INVARIANT_FAILED",
            "Calculation failed",
            500,
        ) from error
