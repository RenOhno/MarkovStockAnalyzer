package com.example.markovstockanalyzer.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AnalysisResultResponse(
        String id,
        String conditionId,
        String datasetId,
        List<String> stateOrder,
        String priceBasis,
        LocalDate asOfDate,
        String currentState,
        Integer sampleCount,
        Integer transitionCount,
        List<List<Integer>> transitionCounts,
        List<List<Double>> transitionMatrix,
        String predictionStatus,
        List<ForecastPayload> forecasts,
        List<AnalysisWarning> warnings,
        DataSourceResponse dataSource,
        ProvenanceResponse provenance,
        String engineVersion,
        Instant createdAt
) {
}
