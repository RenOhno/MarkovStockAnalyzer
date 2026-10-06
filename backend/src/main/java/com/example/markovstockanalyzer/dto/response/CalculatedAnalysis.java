package com.example.markovstockanalyzer.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record CalculatedAnalysis(
        List<String> stateOrder,
        LocalDate asOfDate,
        String currentState,
        Integer sampleCount,
        Integer transitionCount,
        List<List<Integer>> transitionCounts,
        List<List<Double>> transitionMatrix,
        String predictionStatus,
        List<ForecastPayload> forecasts,
        List<AnalysisWarning> warnings,
        String engineVersion,
        Map<String, Object> runtime
) {
}
