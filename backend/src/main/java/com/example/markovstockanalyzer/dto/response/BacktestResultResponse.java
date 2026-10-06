package com.example.markovstockanalyzer.dto.response;

import java.time.Instant;
import java.time.LocalDate;

public record BacktestResultResponse(
        String id, String conditionId, String datasetId, LocalDate testStart, LocalDate testEnd,
        Integer horizon, Integer eligibleCount, Integer predictedCount, Integer correctCount,
        Integer skippedCount, Double coverage, BacktestMetrics metrics, String engineVersion,
        DataSourceResponse dataSource, ProvenanceResponse provenance, Instant createdAt
) {
}
