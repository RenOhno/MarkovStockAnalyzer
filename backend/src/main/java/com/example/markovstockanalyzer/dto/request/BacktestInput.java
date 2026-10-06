package com.example.markovstockanalyzer.dto.request;

import com.example.markovstockanalyzer.dto.response.PriceDatasetPayload;

public record BacktestInput(String requestId, String engineVersion, AnalysisCondition condition,
                            PriceDatasetPayload dataset, BacktestEvaluation evaluation) {
    public BacktestInput(AnalyzeInput input, BacktestEvaluation evaluation) {
        this(input.requestId(), input.engineVersion(), input.condition(), input.dataset(), evaluation);
    }
}
