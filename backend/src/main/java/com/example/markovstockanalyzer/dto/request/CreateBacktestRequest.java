package com.example.markovstockanalyzer.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record CreateBacktestRequest(
        @NotNull @Positive Long conditionId,
        @NotNull @Positive Long datasetId,
        @NotNull LocalDate testStart,
        @NotNull LocalDate testEnd,
        @NotNull @Min(30) Integer minTrainStates,
        @NotNull @Pattern(regexp = "EXPANDING") String trainingMode,
        @JsonProperty(required = true) @Null Integer windowSize,
        @NotNull @Min(1) @Max(1) Integer horizon
) {
    public BacktestEvaluation evaluation() {
        return new BacktestEvaluation(testStart, testEnd, minTrainStates, trainingMode, windowSize, horizon);
    }
}
