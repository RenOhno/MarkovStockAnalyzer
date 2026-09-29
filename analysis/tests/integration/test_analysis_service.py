from datetime import date
from decimal import Decimal

import numpy as np
import pytest

from app.data.calendar import sessions
from app.providers.base import PriceDataset, PricePoint
from app.services.analysis_service import (
    AnalysisService,
    PREDICTION_AVAILABLE,
    PREDICTION_UNAVAILABLE,
)


def make_dataset(state_pattern, provider="FIXTURE"):
    states = (state_pattern * ((len(state_pattern) + 30) // len(state_pattern)))[:31]
    prices = [Decimal("100")]

    for state in states:
        if state == 0:
            prices.append(prices[-1] * Decimal("1.01"))
        elif state == 1:
            prices.append(prices[-1])
        else:
            prices.append(prices[-1] * Decimal("0.98"))

    dates = sessions(date(2020, 1, 1), date(2021, 12, 31))[:len(prices)]

    return PriceDataset(
        ticker="SYNTHETIC",
        provider=provider,
        prices=[
            PricePoint(
                date=day,
                close=f"{price:.10f}",
                adjusted_close=f"{price:.10f}",
                volume=100,
            )
            for day, price in zip(dates, prices, strict=True)
        ],
    )


def test_analysis_service_runs_complete_analysis():
    dataset = make_dataset([0, 0, 1, 2, 0, 2, 1, 0])
    start_date = dataset.prices[0].date
    end_date = dataset.prices[-1].date

    result = AnalysisService().analyze(dataset, start_date, end_date)

    assert result.state_order == ("UP", "FLAT", "DOWN")
    assert result.sample_count == 31
    assert result.transition_count == result.sample_count - 1
    assert sum(map(sum, result.transition_counts)) == result.transition_count
    assert result.current_state == "FLAT"
    assert result.as_of_date == end_date
    assert result.prediction_status == PREDICTION_AVAILABLE
    assert len(result.forecasts) == 4
    assert [forecast.horizon for forecast in result.forecasts] == [1, 3, 5, 10]

    for row in result.transition_matrix:
        assert np.isclose(sum(value for value in row if value is not None), 1.0)
    for forecast in result.forecasts:
        assert np.isclose(sum(forecast.probabilities), 1.0)


def test_analysis_service_rejects_fewer_than_30_states():
    dataset = make_dataset([0, 1, 2])

    with pytest.raises(ValueError, match="INSUFFICIENT_STATES"):
        AnalysisService().analyze(
            dataset,
            dataset.prices[0].date,
            dataset.prices[10].date,
        )


def test_unestimated_row_disables_forecasts_without_filling_matrix():
    dataset = make_dataset([0])

    result = AnalysisService().analyze(
        dataset,
        dataset.prices[0].date,
        dataset.prices[-1].date,
    )

    assert result.prediction_status == PREDICTION_UNAVAILABLE
    assert result.forecasts == []
    assert result.transition_matrix[0][0] == 1.0
    assert result.transition_matrix[1] == [None, None, None]
    assert result.transition_matrix[2] == [None, None, None]
    assert any(
        warning["code"] == "ZERO_ROW_UNESTIMATED"
        for warning in result.warnings
    )
