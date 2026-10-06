package com.example.markovstockanalyzer.dto.request;

import com.example.markovstockanalyzer.dto.response.PriceDatasetPayload;

public record SeriesInput(
        String requestId,
        String engineVersion,
        AnalysisCondition condition,
        PriceDatasetPayload dataset,
        String requiredEngineVersion
) {
    public SeriesInput(AnalyzeInput input, String requiredEngineVersion) {
        this(input.requestId(), input.engineVersion(), input.condition(), input.dataset(), requiredEngineVersion);
    }
}
