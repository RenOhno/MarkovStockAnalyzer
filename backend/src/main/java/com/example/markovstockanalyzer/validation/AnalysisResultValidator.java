package com.example.markovstockanalyzer.validation;

import com.example.markovstockanalyzer.dto.request.AnalyzeInput;
import com.example.markovstockanalyzer.dto.response.CalculatedAnalysis;
import com.example.markovstockanalyzer.dto.response.ForecastPayload;
import com.example.markovstockanalyzer.exception.CalculationInvariantFailedException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AnalysisResultValidator {
    private static final List<String> STATE_ORDER = List.of("UP", "FLAT", "DOWN");
    private static final List<Integer> HORIZONS = List.of(1, 3, 5, 10);
    // docs/system-design.md section 10.4 permits absolute error 1e-9 at DB/API boundaries.
    public static final double PROBABILITY_SUM_TOLERANCE = 1e-9;

    public void validate(CalculatedAnalysis result) {
        validate(result, AnalyzeInput.ENGINE_VERSION);
    }

    public void validate(CalculatedAnalysis result, String expectedEngineVersion) {
        require(result != null, "CalculatedAnalysis is required");
        require(STATE_ORDER.equals(result.stateOrder()), "stateOrder must be UP, FLAT, DOWN");
        require(result.currentState() != null && STATE_ORDER.contains(result.currentState()),
                "currentState is invalid");
        require(expectedEngineVersion != null && !expectedEngineVersion.isBlank()
                        && expectedEngineVersion.equals(result.engineVersion()), "engineVersion does not match");
        require(result.asOfDate() != null && result.warnings() != null && result.runtime() != null,
                "asOfDate, warnings and runtime are required");
        require(result.sampleCount() != null && result.sampleCount() >= 30,
                "sampleCount must be at least 30");
        require(result.transitionCount() != null && result.transitionCount() >= 0
                        && result.transitionCount() == result.sampleCount() - 1,
                "transitionCount must equal sampleCount minus one");
        requireMatrix(result.transitionCounts(), "transitionCounts");
        requireMatrix(result.transitionMatrix(), "transitionMatrix");

        long total = 0;
        boolean hasUnestimatedRow = false;
        for (int row = 0; row < 3; row++) {
            long rowCount = 0;
            for (Integer count : result.transitionCounts().get(row)) {
                require(count != null && count >= 0, "transitionCounts must contain nonnegative integers");
                rowCount += count;
            }
            total += rowCount;
            List<Double> probabilities = result.transitionMatrix().get(row);
            if (rowCount == 0) {
                require(probabilities.stream().allMatch(value -> value == null),
                        "An unestimated row must be [null, null, null]");
                hasUnestimatedRow = true;
            } else {
                requireDistribution(probabilities, "transitionMatrix row");
            }
        }
        require(total == result.transitionCount(), "transitionCounts total must equal transitionCount");
        require(result.forecasts() != null, "forecasts are required");

        if (hasUnestimatedRow) {
            require("UNAVAILABLE".equals(result.predictionStatus()),
                    "Unestimated rows require predictionStatus UNAVAILABLE");
            require(result.forecasts().isEmpty(), "UNAVAILABLE forecasts must be empty");
            require(result.warnings().stream().anyMatch(warning -> warning != null
                            && "ZERO_ROW_UNESTIMATED".equals(warning.code())),
                    "Unestimated rows require a ZERO_ROW_UNESTIMATED warning");
        } else {
            require("AVAILABLE".equals(result.predictionStatus()),
                    "Estimated rows require predictionStatus AVAILABLE");
            require(result.forecasts().size() == HORIZONS.size(), "AVAILABLE requires four forecasts");
            for (int index = 0; index < HORIZONS.size(); index++) {
                ForecastPayload forecast = result.forecasts().get(index);
                require(forecast != null && HORIZONS.get(index).equals(forecast.horizon()),
                        "forecast horizons must be 1, 3, 5, 10 in order");
                requireDistribution(forecast.probabilities(), "forecast probabilities");
            }
        }
    }

    private void requireMatrix(List<? extends List<?>> matrix, String field) {
        require(matrix != null && matrix.size() == 3, field + " must be 3x3");
        for (List<?> row : matrix) {
            require(row != null && row.size() == 3, field + " must be 3x3");
        }
    }

    private void requireDistribution(List<Double> probabilities, String field) {
        require(probabilities != null && probabilities.size() == 3, field + " must have three elements");
        double sum = 0;
        for (Double value : probabilities) {
            require(value != null && Double.isFinite(value) && value >= 0 && value <= 1,
                    field + " must contain finite probabilities in [0, 1]");
            sum += value;
        }
        require(Math.abs(sum - 1.0) <= PROBABILITY_SUM_TOLERANCE, field + " must sum to one");
    }

    private void require(boolean valid, String message) {
        if (!valid) {
            throw new CalculationInvariantFailedException(message);
        }
    }
}
