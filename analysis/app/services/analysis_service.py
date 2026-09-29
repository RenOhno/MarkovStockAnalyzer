from dataclasses import dataclass
from datetime import date
from typing import Any

from app.core.markov import forecast_distribution
from app.core.returns import calculate_returns
from app.core.state_classifier import STATE_ORDER, classify_returns
from app.core.transition import count_transitions, estimate_mle
from app.data.preprocess import (
    extract_adjusted_closes,
    preprocess_price_dataset,
)
from app.providers.base import PriceDataset


HORIZONS = (1, 3, 5, 10)
PREDICTION_AVAILABLE = "AVAILABLE"
PREDICTION_UNAVAILABLE = "UNAVAILABLE"


@dataclass(frozen=True)
class ForecastResult:
    horizon: int
    probabilities: list[float]


@dataclass(frozen=True)
class AnalysisResult:
    state_order: tuple[str, str, str]
    as_of_date: date
    current_state: str
    sample_count: int
    transition_count: int
    transition_counts: list[list[int]]
    transition_matrix: list[list[float | None]]
    prediction_status: str
    forecasts: list[ForecastResult]
    warnings: list[dict[str, Any]]


class AnalysisService:
    """前処理済み価格から通常分析結果を組み立てる。"""

    def __init__(self, minimum_states: int = 30):
        self.minimum_states = minimum_states

    def analyze(
        self,
        dataset: PriceDataset,
        start_date: date,
        end_date: date,
        lower_threshold: str = "-0.005",
        upper_threshold: str = "0.005",
    ) -> AnalysisResult:
        if start_date > end_date:
            raise ValueError("INVALID_DATE_RANGE")

        normalized = preprocess_price_dataset(dataset)
        adjusted_closes = extract_adjusted_closes(normalized)

        dates = [price.date for price in normalized.prices]
        selected_indices = [
            index
            for index, price_date in enumerate(dates)
            if start_date <= price_date <= end_date
        ]
        if not selected_indices:
            raise ValueError("INVALID_ANALYSIS_RANGE")

        first_index = selected_indices[0]
        last_index = selected_indices[-1]
        price_start = max(0, first_index - 1)
        price_values = adjusted_closes[price_start : last_index + 1]

        returns = calculate_returns(price_values)
        states = classify_returns(
            returns,
            lower=lower_threshold,
            upper=upper_threshold,
        )

        sample_count = len(states)
        if sample_count < self.minimum_states:
            raise ValueError("INSUFFICIENT_STATES")

        transition_counts = count_transitions(states)
        transition_matrix, unestimated_rows = estimate_mle(transition_counts)
        current_state_number = int(states[-1])

        warnings = _build_warnings(
            dataset=normalized,
            transition_counts=transition_counts,
            unestimated_rows=unestimated_rows,
        )
        prediction_status = (
            PREDICTION_UNAVAILABLE
            if len(unestimated_rows) > 0
            else PREDICTION_AVAILABLE
        )

        forecasts: list[ForecastResult] = []
        if prediction_status == PREDICTION_AVAILABLE:
            forecasts = [
                ForecastResult(
                    horizon=horizon,
                    probabilities=forecast_distribution(
                        transition_matrix,
                        current_state_number,
                        horizon,
                    ).tolist(),
                )
                for horizon in HORIZONS
            ]

        return AnalysisResult(
            state_order=STATE_ORDER,
            as_of_date=dates[last_index],
            current_state=STATE_ORDER[current_state_number],
            sample_count=sample_count,
            transition_count=int(transition_counts.sum()),
            transition_counts=transition_counts.tolist(),
            transition_matrix=_matrix_to_result(transition_matrix),
            prediction_status=prediction_status,
            forecasts=forecasts,
            warnings=warnings,
        )


def analyze(
    dataset: PriceDataset,
    start_date: date,
    end_date: date,
    lower_threshold: str = "-0.005",
    upper_threshold: str = "0.005",
) -> AnalysisResult:
    """AnalysisServiceの標準設定で通常分析を実行する。"""
    return AnalysisService().analyze(
        dataset,
        start_date,
        end_date,
        lower_threshold,
        upper_threshold,
    )


def _matrix_to_result(matrix) -> list[list[float | None]]:
    return [
        [None if value != value else float(value) for value in row]
        for row in matrix
    ]


def _build_warnings(
    dataset: PriceDataset,
    transition_counts,
    unestimated_rows,
) -> list[dict[str, Any]]:
    warnings: list[dict[str, Any]] = []

    low_support_rows = [
        STATE_ORDER[index]
        for index, support in enumerate(transition_counts.sum(axis=1))
        if support < 20
    ]
    if low_support_rows:
        warnings.append(
            {"code": "LOW_ROW_SUPPORT", "states": low_support_rows}
        )

    if dataset.provider == "FIXTURE":
        warnings.append({"code": "SYNTHETIC_FIXTURE", "states": []})

    if len(unestimated_rows) > 0:
        warnings.append(
            {
                "code": "ZERO_ROW_UNESTIMATED",
                "states": [STATE_ORDER[index] for index in unestimated_rows],
            }
        )

    return warnings
