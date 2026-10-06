package com.example.markovstockanalyzer.dto.request;

import com.example.markovstockanalyzer.dto.response.PriceDatasetPayload;

public record AnalyzeInput(
        String requestId,
        String engineVersion,
        AnalysisCondition condition,
        PriceDatasetPayload dataset
) {
    public static final String ENGINE_VERSION = "msa-core-v1";

    public AnalyzeInput(String requestId, AnalysisCondition condition, PriceDatasetPayload dataset) {
        this(requestId, ENGINE_VERSION, condition, dataset);
    }
}
