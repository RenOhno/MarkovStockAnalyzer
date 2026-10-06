package com.example.markovstockanalyzer.model;

import com.example.markovstockanalyzer.dto.response.CalculatedAnalysis;

import java.time.Instant;

public record AnalysisResult(
        Long id,
        Long conditionId,
        Long datasetId,
        CalculatedAnalysis calculatedAnalysis,
        Instant createdAt
) {
}
