package com.example.markovstockanalyzer.validation;

import com.example.markovstockanalyzer.dto.request.BacktestInput;
import com.example.markovstockanalyzer.dto.response.*;
import com.example.markovstockanalyzer.exception.CalculationInvariantFailedException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.example.markovstockanalyzer.validation.AnalysisResultValidator.PROBABILITY_SUM_TOLERANCE;

@Component
public class BacktestResultValidator {
    private static final List<String> STATES = List.of("UP", "FLAT", "DOWN");

    public void validate(CalculatedBacktest result, BacktestInput input) {
        require(result != null && result.summary() != null && result.predictions() != null && result.runtime() != null,
                "Backtest response fields are required");
        require(input.engineVersion().equals(result.engineVersion()), "engineVersion does not match");
        BacktestSummary summary = result.summary();
        require(input.evaluation().testStart().equals(summary.testStart())
                && input.evaluation().testEnd().equals(summary.testEnd())
                && input.evaluation().horizon().equals(summary.horizon()), "Evaluation summary does not match input");
        require(summary.eligibleCount() != null && summary.eligibleCount() >= 1 && summary.eligibleCount() <= 1000,
                "eligibleCount must be between 1 and 1000");
        require(nonnegative(summary.predictedCount()) && nonnegative(summary.skippedCount())
                && nonnegative(summary.correctCount()), "Counts must be nonnegative");
        require((long) summary.predictedCount() + summary.skippedCount() == summary.eligibleCount()
                && summary.correctCount() <= summary.predictedCount(), "Summary counts are inconsistent");
        require(result.predictions().size() == summary.eligibleCount(), "Prediction count does not match eligibleCount");
        require(rate(summary.coverage()) && near(summary.coverage(), (double) summary.predictedCount() / summary.eligibleCount()),
                "coverage does not match counts");
        validateMetrics(summary);

        int scored = 0;
        int skipped = 0;
        int correct = 0;
        LocalDate previousTarget = null;
        Map<String, Integer> skips = new java.util.HashMap<>();
        for (BacktestPrediction prediction : result.predictions()) {
            require(prediction != null, "Prediction is required");
            require(prediction.trainStart() != null && prediction.trainEnd() != null
                    && prediction.originDate() != null && prediction.targetDate() != null
                    && !prediction.trainStart().isAfter(prediction.trainEnd())
                    && prediction.trainEnd().equals(prediction.originDate())
                    && prediction.originDate().isBefore(prediction.targetDate()), "Prediction dates are inconsistent");
            require(!prediction.trainStart().isBefore(input.condition().startDate())
                    && !prediction.targetDate().isBefore(summary.testStart())
                    && !prediction.targetDate().isAfter(summary.testEnd()), "Prediction is outside evaluation/condition range");
            require(previousTarget == null || previousTarget.isBefore(prediction.targetDate()),
                    "Predictions must be ordered with unique target dates");
            previousTarget = prediction.targetDate();
            require(state(prediction.actualState()), "actualState is invalid");
            if ("SCORED".equals(prediction.status())) {
                require(state(prediction.predictedState()) && state(prediction.majorityState())
                        && state(prediction.persistenceState()) && prediction.skipCode() == null,
                        "SCORED fields are inconsistent");
                distribution(prediction.probabilities());
                scored++;
                if (prediction.actualState().equals(prediction.predictedState())) {
                    correct++;
                }
            } else if ("SKIPPED".equals(prediction.status())) {
                require(prediction.predictedState() == null && prediction.probabilities() == null
                        && prediction.majorityState() == null && prediction.persistenceState() == null
                        && code(prediction.skipCode()), "SKIPPED fields are inconsistent");
                skipped++;
                skips.merge(prediction.skipCode(), 1, Integer::sum);
            } else {
                require(false, "Prediction status is invalid");
            }
        }
        require(scored == summary.predictedCount() && skipped == summary.skippedCount()
                && correct == summary.correctCount(), "Prediction statuses/labels do not match summary counts");
        require(skips.equals(summary.metrics().skipReasons()), "skipReasons do not match skipped predictions");
    }

    private void validateMetrics(BacktestSummary summary) {
        BacktestMetrics metrics = summary.metrics();
        require(metrics != null, "metrics are required");
        nullableVector(metrics.precision());
        nullableVector(metrics.recall());
        List<List<Integer>> matrix = metrics.confusionMatrix();
        require(matrix != null && matrix.size() == 3, "confusionMatrix must be 3x3");
        long total = 0;
        long diagonal = 0;
        for (int i = 0; i < 3; i++) {
            List<Integer> row = matrix.get(i);
            require(row != null && row.size() == 3, "confusionMatrix must be 3x3");
            for (Integer value : row) {
                require(nonnegative(value), "confusionMatrix must be nonnegative");
                total += value;
            }
            diagonal += row.get(i);
        }
        require(total == summary.predictedCount() && diagonal == summary.correctCount(),
                "Confusion totals do not match summary");
        require(nonnegative(metrics.tieCount()) && metrics.tieCount() <= summary.predictedCount(), "tieCount is invalid");
        require(metrics.skipReasons() != null, "skipReasons are required");
        long skipTotal = 0;
        for (var entry : metrics.skipReasons().entrySet()) {
            require(code(entry.getKey()) && nonnegative(entry.getValue()), "skipReasons are invalid");
            skipTotal += entry.getValue();
        }
        require(skipTotal == summary.skippedCount(), "Skip reason total does not match skippedCount");
        if (summary.predictedCount() == 0) {
            require(metrics.accuracy() == null && metrics.majorityAccuracy() == null && metrics.persistenceAccuracy() == null
                    && metrics.brierScore() == null && metrics.logLoss() == null, "Unscored metrics must be null");
            require(metrics.precision().stream().allMatch(value -> value == null)
                    && metrics.recall().stream().allMatch(value -> value == null), "Unscored precision/recall must be null");
        } else {
            require(rate(metrics.accuracy()) && near(metrics.accuracy(), (double) summary.correctCount() / summary.predictedCount()),
                    "accuracy does not match counts");
            require(rate(metrics.majorityAccuracy()) && rate(metrics.persistenceAccuracy()), "Baseline accuracy is invalid");
            require(finiteNonnegative(metrics.brierScore()) && metrics.brierScore() <= 2
                    && finiteNonnegative(metrics.logLoss()), "Probability metrics are invalid");
        }
    }

    private void distribution(List<Double> probabilities) {
        require(probabilities != null && probabilities.size() == 3, "probabilities must have three elements");
        double sum = 0;
        for (Double probability : probabilities) {
            require(rate(probability), "probabilities must be finite and in [0, 1]");
            sum += probability;
        }
        require(near(sum, 1), "probabilities must sum to one");
    }

    private void nullableVector(List<Double> values) {
        require(values != null && values.size() == 3, "precision/recall must have three elements");
        for (Double value : values) {
            require(value == null || rate(value), "precision/recall is invalid");
        }
    }

    private boolean nonnegative(Integer value) { return value != null && value >= 0; }
    private boolean rate(Double value) { return value != null && Double.isFinite(value) && value >= 0 && value <= 1; }
    private boolean finiteNonnegative(Double value) { return value != null && Double.isFinite(value) && value >= 0; }
    private boolean state(String value) { return value != null && STATES.contains(value); }
    private boolean code(String value) { return value != null && value.matches("[A-Z][A-Z0-9_]{0,127}"); }
    private boolean near(double actual, double expected) { return Math.abs(actual - expected) <= PROBABILITY_SUM_TOLERANCE; }
    private void require(boolean valid, String message) {
        if (!valid) { throw new CalculationInvariantFailedException(message); }
    }
}
