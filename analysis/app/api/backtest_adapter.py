from datetime import date

import numpy as np

from app.api.analysis_adapter import (
    _runtime,
    dataset_from_payload,
    validate_condition_dataset,
    validate_preprocessed_dataset,
)
from app.core.returns import calculate_returns
from app.core.state_classifier import classify_returns
from app.core.backtest import BacktestResult
from app.core.metrics import MetricsResult
from app.schemas import (
    BacktestEvaluation,
    BacktestMetricsPayload,
    BacktestPredictionPayload,
    BacktestSummary,
    CalculatedBacktest,
    AnalysisCondition,
    ENGINE_VERSION,
    PriceDatasetPayload,
)
from app.providers.base import PriceDataset


MAX_EVALUATION_CANDIDATES = 1000


def prepare_backtest_dataset(
    condition: AnalysisCondition,
    payload: PriceDatasetPayload,
) -> tuple[PriceDataset, list[int], list[date]]:
    dataset = dataset_from_payload(payload)
    validate_condition_dataset(condition, dataset)
    dataset = validate_preprocessed_dataset(dataset)

    prices = dataset.prices
    first_condition_index = next(
        (
            index
            for index, price in enumerate(prices)
            if price.date >= condition.startDate
        ),
        None,
    )
    if first_condition_index is None or first_condition_index == 0:
        raise ValueError("DATASET_CONDITION_MISMATCH")

    last_condition_index = max(
        (
            index
            for index, price in enumerate(prices)
            if price.date <= condition.endDate
        ),
        default=None,
    )
    if last_condition_index is None or last_condition_index < first_condition_index:
        raise ValueError("DATASET_CONDITION_MISMATCH")

    selected_prices = prices[first_condition_index - 1 : last_condition_index + 1]
    returns = calculate_returns(
        [price.adjusted_close for price in selected_prices]
    )
    states = classify_returns(
        returns,
        lower=str(condition.lowerThreshold),
        upper=str(condition.upperThreshold),
    )
    state_dates = [price.date for price in selected_prices[1:]]
    return dataset, states.tolist(), state_dates


def evaluation_dates(
    dates: list[date],
    evaluation: BacktestEvaluation,
) -> list[date]:
    return [
        target_date
        for target_date in dates
        if evaluation.testStart <= target_date <= evaluation.testEnd
    ]


def build_backtest_response(
    result: BacktestResult,
    metrics: MetricsResult,
    evaluation: BacktestEvaluation,
    input_content_sha256: str,
) -> CalculatedBacktest:
    _validate_backtest_invariants(result, metrics, evaluation)
    predictions = [
        BacktestPredictionPayload(
            originDate=prediction.origin_date,
            targetDate=prediction.target_date,
            trainStart=prediction.train_start,
            trainEnd=prediction.train_end,
            actualState=prediction.actual_state,
            predictedState=prediction.predicted_state,
            probabilities=prediction.probabilities,
            majorityState=prediction.majority_state,
            persistenceState=prediction.persistence_state,
            status=prediction.status,
            skipCode=prediction.skip_code,
        )
        for prediction in result.predictions
    ]
    metrics_payload = BacktestMetricsPayload(
        accuracy=metrics.accuracy,
        precision=metrics.precision,
        recall=metrics.recall,
        confusionMatrix=metrics.confusion_matrix,
        brierScore=metrics.brier_score,
        logLoss=metrics.log_loss,
        majorityAccuracy=metrics.majority_accuracy,
        persistenceAccuracy=metrics.persistence_accuracy,
        tieCount=metrics.tie_count,
        skipReasons=metrics.skip_reasons,
    )
    summary = BacktestSummary(
        testStart=evaluation.testStart,
        testEnd=evaluation.testEnd,
        horizon=evaluation.horizon,
        eligibleCount=metrics.eligible_count,
        predictedCount=metrics.predicted_count,
        correctCount=metrics.correct_count,
        skippedCount=metrics.skipped_count,
        coverage=metrics.coverage,
        metrics=metrics_payload,
    )
    return CalculatedBacktest(
        summary=summary,
        predictions=predictions,
        engineVersion=ENGINE_VERSION,
        runtime=_runtime(input_content_sha256),
    )


def _validate_backtest_invariants(
    result: BacktestResult,
    metrics: MetricsResult,
    evaluation: BacktestEvaluation,
) -> None:
    predictions = result.predictions
    if not predictions or len(predictions) != metrics.eligible_count:
        raise ValueError("CALCULATION_INVARIANT_FAILED")
    if metrics.eligible_count > MAX_EVALUATION_CANDIDATES:
        raise ValueError("CALCULATION_INVARIANT_FAILED")
    if metrics.predicted_count + metrics.skipped_count != metrics.eligible_count:
        raise ValueError("CALCULATION_INVARIANT_FAILED")
    if metrics.correct_count > metrics.predicted_count:
        raise ValueError("CALCULATION_INVARIANT_FAILED")
    if metrics.predicted_count != metrics.baseline_compared_count:
        raise ValueError("CALCULATION_INVARIANT_FAILED")
    if metrics.coverage != metrics.predicted_count / metrics.eligible_count:
        raise ValueError("CALCULATION_INVARIANT_FAILED")

    for prediction in predictions:
        if not (
            prediction.train_start <= prediction.train_end
            and prediction.train_end == prediction.origin_date
            and prediction.origin_date < prediction.target_date
            and evaluation.testStart <= prediction.target_date <= evaluation.testEnd
        ):
            raise ValueError("CALCULATION_INVARIANT_FAILED")
        if prediction.actual_state not in {"UP", "FLAT", "DOWN"}:
            raise ValueError("CALCULATION_INVARIANT_FAILED")

        if prediction.status == "SCORED":
            if (
                prediction.predicted_state is None
                or prediction.probabilities is None
                or len(prediction.probabilities) != 3
                or prediction.majority_state is None
                or prediction.persistence_state is None
                or prediction.skip_code is not None
            ):
                raise ValueError("CALCULATION_INVARIANT_FAILED")
            probabilities = np.asarray(prediction.probabilities, dtype=float)
            if (
                not np.isfinite(probabilities).all()
                or np.any(probabilities < 0)
                or not np.isclose(probabilities.sum(), 1.0, atol=1e-12, rtol=0)
            ):
                raise ValueError("CALCULATION_INVARIANT_FAILED")
        elif prediction.status == "SKIPPED":
            if (
                prediction.predicted_state is not None
                or prediction.probabilities is not None
                or prediction.majority_state is not None
                or prediction.persistence_state is not None
                or not prediction.skip_code
            ):
                raise ValueError("CALCULATION_INVARIANT_FAILED")
        else:
            raise ValueError("CALCULATION_INVARIANT_FAILED")

    if sum(map(sum, metrics.confusion_matrix)) != metrics.predicted_count:
        raise ValueError("CALCULATION_INVARIANT_FAILED")
    if sum(
        metrics.confusion_matrix[index][index]
        for index in range(3)
    ) != metrics.correct_count:
        raise ValueError("CALCULATION_INVARIANT_FAILED")
