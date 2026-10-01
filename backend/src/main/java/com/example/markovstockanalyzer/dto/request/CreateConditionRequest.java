package com.example.markovstockanalyzer.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateConditionRequest(
        @NotBlank String name,
        @NotBlank String stockId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull BigDecimal lowerThreshold,
        @NotNull BigDecimal upperThreshold,
        @NotNull Integer stateCount,
        @NotBlank String estimator,
        @NotBlank String windowMode,
        Integer windowSize,
        @NotNull List<Integer> horizons
) {
}
