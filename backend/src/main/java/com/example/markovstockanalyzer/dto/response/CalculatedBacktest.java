package com.example.markovstockanalyzer.dto.response;

import java.util.List;
import java.util.Map;

public record CalculatedBacktest(BacktestSummary summary, List<BacktestPrediction> predictions,
                                 String engineVersion, Map<String, Object> runtime) {
}
