package com.example.markovstockanalyzer.service;

import com.example.markovstockanalyzer.dto.response.CalculatedAnalysis;
import com.example.markovstockanalyzer.dto.response.ConditionResponse;
import com.example.markovstockanalyzer.dto.response.PriceDatasetPayload;

public record AnalysisExecutionResult(
        ConditionResponse condition,
        PriceDatasetPayload dataset,
        CalculatedAnalysis calculatedAnalysis
) {
}
