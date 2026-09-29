from datetime import date
import math

from app.api.analysis_adapter import (
    dataset_from_payload,
    validate_condition_dataset,
    validate_preprocessed_dataset,
)
from app.api.limits import validate_state_count
from app.core.returns import calculate_returns
from app.core.state_classifier import STATE_ORDER, classify_returns
from app.data.payload import PRICE_BASIS
from app.providers.base import PriceDataset
from app.schemas import (
    AnalysisCondition,
    CalculatedSeries,
    ENGINE_VERSION,
    PriceDatasetPayload,
    SeriesPointPayload,
)


def build_series_response(
    condition: AnalysisCondition,
    payload: PriceDatasetPayload,
) -> CalculatedSeries:
    dataset = dataset_from_payload(payload)
    validate_condition_dataset(condition, dataset)
    dataset = validate_preprocessed_dataset(dataset)
    points = _build_points(condition, dataset)
    validate_state_count(len(points))
    _validate_points(points, condition)
    return CalculatedSeries(
        priceBasis=PRICE_BASIS,
        points=points,
        engineVersion=ENGINE_VERSION,
    )


def _build_points(
    condition: AnalysisCondition,
    dataset: PriceDataset,
) -> list[SeriesPointPayload]:
    prices = dataset.prices
    first_index = next(
        (
            index
            for index, price in enumerate(prices)
            if price.date >= condition.startDate
        ),
        None,
    )
    last_index = max(
        (
            index
            for index, price in enumerate(prices)
            if price.date <= condition.endDate
        ),
        default=None,
    )
    if (
        first_index is None
        or first_index == 0
        or last_index is None
        or last_index < first_index
    ):
        raise ValueError("DATASET_CONDITION_MISMATCH")

    selected_prices = prices[first_index - 1 : last_index + 1]
    returns = calculate_returns(
        [price.adjusted_close for price in selected_prices]
    )
    states = classify_returns(
        returns,
        lower=str(condition.lowerThreshold),
        upper=str(condition.upperThreshold),
    )
    points = []
    for price, return_value, state in zip(
        selected_prices[1:],
        returns,
        states,
        strict=True,
    ):
        numeric_return = float(return_value)
        if not math.isfinite(numeric_return):
            raise ValueError("CALCULATION_INVARIANT_FAILED")
        points.append(
            SeriesPointPayload(
                date=price.date,
                close=price.close,
                adjustedClose=price.adjusted_close,
                returnValue=numeric_return,
                state=STATE_ORDER[int(state)],
            )
        )
    return points


def _validate_points(
    points: list[SeriesPointPayload],
    condition: AnalysisCondition,
) -> None:
    if not points:
        raise ValueError("CALCULATION_INVARIANT_FAILED")
    if any(
        current.date <= previous.date
        for previous, current in zip(points, points[1:])
    ):
        raise ValueError("CALCULATION_INVARIANT_FAILED")
    if any(
        point.date < condition.startDate or point.date > condition.endDate
        for point in points
    ):
        raise ValueError("CALCULATION_INVARIANT_FAILED")
    if any(not math.isfinite(point.returnValue) for point in points):
        raise ValueError("CALCULATION_INVARIANT_FAILED")
