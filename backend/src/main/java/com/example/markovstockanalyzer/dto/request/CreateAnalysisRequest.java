package com.example.markovstockanalyzer.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateAnalysisRequest(
        @NotNull @Positive Long conditionId,
        @Positive Long datasetId
) {
}
