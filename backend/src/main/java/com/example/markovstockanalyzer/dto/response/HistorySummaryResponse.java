package com.example.markovstockanalyzer.dto.response;

import java.time.Instant;
import java.time.LocalDate;

public record HistorySummaryResponse(
        String type, String id, String conditionId, String stockId, String conditionName,
        LocalDate startDate, LocalDate endDate, String datasetId, String predictionStatus,
        String engineVersion, Instant createdAt, Instant dataFetchedAt
) {
}
