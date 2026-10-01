package com.example.markovstockanalyzer.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ConditionResponse(
        Long id,
        String name,
        String stockId,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal lowerThreshold,
        BigDecimal upperThreshold,
        Integer stateCount,
        String estimator,
        String windowMode,
        Integer windowSize,
        List<Integer> horizons,
        Instant createdAt
) {
}
