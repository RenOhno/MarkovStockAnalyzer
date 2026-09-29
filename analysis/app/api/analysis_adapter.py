import platform
import subprocess
from datetime import datetime, timezone
from importlib.metadata import version

import numpy as np

from app.core.state_classifier import STATE_ORDER
from app.data.calendar import previous_session, sessions
from app.data.payload import (
    ADJUSTMENT_POLICY,
    EXCHANGE,
    NORMALIZATION_VERSION,
    PRICE_BASIS,
    PRICE_SCALE,
    ROUNDING_MODE,
    TIME_ZONE,
    content_sha256,
    normalize_price_dataset,
)
from app.data.preprocess import preprocess_price_dataset
from app.providers.base import PriceDataset, PricePoint
from app.schemas import (
    AnalysisCondition,
    AnalysisWarning,
    CalculatedAnalysis,
    ENGINE_VERSION,
    ForecastPayload,
    PriceDatasetPayload,
)


CONFIGURATION_VERSION = "analysis-config-v1"


def dataset_from_payload(payload: PriceDatasetPayload) -> PriceDataset:
    if payload.exchange != EXCHANGE or payload.timeZone != TIME_ZONE:
        raise ValueError("INVALID_DATASET")
    if payload.priceBasis != PRICE_BASIS:
        raise ValueError("INVALID_DATASET")
    if payload.adjustmentPolicy != ADJUSTMENT_POLICY:
        raise ValueError("INVALID_DATASET")
    if payload.provider != payload.metadata.provider:
        raise ValueError("INVALID_DATASET")
    if payload.providerVersion != payload.metadata.providerVersion:
        raise ValueError("INVALID_DATASET")
    if payload.ticker != payload.metadata.ticker:
        raise ValueError("INVALID_DATASET")
    if payload.metadata.exchange != EXCHANGE:
        raise ValueError("INVALID_DATASET")
    if payload.metadata.timeZone != TIME_ZONE:
        raise ValueError("INVALID_DATASET")
    if payload.metadata.priceBasis != PRICE_BASIS:
        raise ValueError("INVALID_DATASET")
    if payload.metadata.schemaVersion != 1:
        raise ValueError("INVALID_DATASET")
    if payload.metadata.normalizationVersion != NORMALIZATION_VERSION:
        raise ValueError("INVALID_DATASET")
    if payload.metadata.roundingMode != ROUNDING_MODE:
        raise ValueError("INVALID_DATASET")
    if payload.metadata.scale != PRICE_SCALE:
        raise ValueError("INVALID_DATASET")
    if payload.fetchedAt.tzinfo is None or payload.fetchedAt.utcoffset() is None:
        raise ValueError("INVALID_DATASET")
    if not payload.prices:
        raise ValueError("INVALID_DATASET")

    prices = [
        PricePoint(
            date=price.date,
            close=price.close,
            adjusted_close=price.adjustedClose,
            volume=price.volume,
        )
        for price in payload.prices
    ]
    dataset = PriceDataset(
        ticker=payload.ticker,
        provider=payload.provider,
        prices=prices,
    )
    normalized = normalize_price_dataset(dataset)
    if normalized.prices != dataset.prices:
        raise ValueError("INVALID_DATASET")
    if payload.coverageStart != prices[0].date:
        raise ValueError("INVALID_DATASET")
    if payload.coverageEnd != prices[-1].date:
        raise ValueError("INVALID_DATASET")
    if any(
        current.date <= previous.date
        for previous, current in zip(prices, prices[1:])
    ):
        raise ValueError("INVALID_DATASET")
    if content_sha256(dataset, payload.providerVersion) != payload.contentSha256:
        raise ValueError("DATASET_HASH_MISMATCH")
    return dataset


def validate_condition_dataset(
    condition: AnalysisCondition,
    dataset: PriceDataset,
) -> None:
    dates = {price.date for price in dataset.prices}
    try:
        required_sessions = set(
            sessions(condition.startDate, condition.endDate)
        )
        prior_session = previous_session(condition.startDate)
    except ValueError as error:
        raise ValueError("DATASET_CONDITION_MISMATCH") from error

    if not required_sessions or not required_sessions.issubset(dates):
        raise ValueError("DATASET_CONDITION_MISMATCH")
    if prior_session not in dates:
        raise ValueError("DATASET_CONDITION_MISMATCH")


def validate_preprocessed_dataset(dataset: PriceDataset) -> PriceDataset:
    try:
        return preprocess_price_dataset(dataset)
    except ValueError as error:
        raise ValueError(str(error)) from error


def build_analysis_response(
    result,
    input_content_sha256: str,
) -> CalculatedAnalysis:
    _validate_analysis_invariants(result)
    forecasts = [
        ForecastPayload(
            horizon=forecast.horizon,
            probabilities=forecast.probabilities,
        )
        for forecast in result.forecasts
    ]
    warnings = [
        AnalysisWarning(
            code=warning["code"],
            states=warning.get("states", []),
        )
        for warning in result.warnings
    ]
    return CalculatedAnalysis(
        stateOrder=list(result.state_order),
        asOfDate=result.as_of_date,
        currentState=result.current_state,
        sampleCount=result.sample_count,
        transitionCount=result.transition_count,
        transitionCounts=result.transition_counts,
        transitionMatrix=result.transition_matrix,
        predictionStatus=result.prediction_status,
        forecasts=forecasts,
        warnings=warnings,
        engineVersion=ENGINE_VERSION,
        runtime=_runtime(input_content_sha256),
    )


def _validate_analysis_invariants(result) -> None:
    if result.state_order != STATE_ORDER:
        raise ValueError("CALCULATION_INVARIANT_FAILED")
    if result.transition_count != result.sample_count - 1:
        raise ValueError("CALCULATION_INVARIANT_FAILED")
    if sum(map(sum, result.transition_counts)) != result.transition_count:
        raise ValueError("CALCULATION_INVARIANT_FAILED")
    if len(result.transition_matrix) != 3 or any(
        len(row) != 3 for row in result.transition_matrix
    ):
        raise ValueError("CALCULATION_INVARIANT_FAILED")
    for row in result.transition_matrix:
        known_values = [value for value in row if value is not None]
        if not np.isfinite(known_values).all() or any(
            value < 0 for value in known_values
        ):
            raise ValueError("CALCULATION_INVARIANT_FAILED")

    if result.prediction_status == "AVAILABLE":
        if any(value is None for row in result.transition_matrix for value in row):
            raise ValueError("CALCULATION_INVARIANT_FAILED")
        if [forecast.horizon for forecast in result.forecasts] != [1, 3, 5, 10]:
            raise ValueError("CALCULATION_INVARIANT_FAILED")
        for forecast in result.forecasts:
            probabilities = np.asarray(forecast.probabilities, dtype=float)
            if (
                len(probabilities) != 3
                or not np.isfinite(probabilities).all()
                or np.any(probabilities < 0)
                or not np.isclose(probabilities.sum(), 1.0, atol=1e-12, rtol=0)
            ):
                raise ValueError("CALCULATION_INVARIANT_FAILED")
    elif result.prediction_status == "UNAVAILABLE":
        if not any(
            all(value is None for value in row)
            for row in result.transition_matrix
        ) or result.forecasts:
            raise ValueError("CALCULATION_INVARIANT_FAILED")
    else:
        raise ValueError("CALCULATION_INVARIANT_FAILED")


def _runtime(input_content_sha256: str) -> dict[str, object]:
    runtime: dict[str, object] = {
        "engineVersion": ENGINE_VERSION,
        "configurationVersion": CONFIGURATION_VERSION,
        "normalizationVersion": NORMALIZATION_VERSION,
        "inputContentSha256": input_content_sha256,
        "pythonVersion": platform.python_version(),
        "numpyVersion": version("numpy"),
        "pandasVersion": version("pandas"),
        "createdAt": datetime.now(timezone.utc).isoformat(),
    }
    git_commit = _git_commit()
    if git_commit is not None:
        runtime["gitCommit"] = git_commit
    return runtime


def _git_commit() -> str | None:
    try:
        result = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            capture_output=True,
            text=True,
            check=True,
            timeout=2,
        )
    except (OSError, subprocess.SubprocessError):
        return None
    commit = result.stdout.strip()
    return commit if commit else None
