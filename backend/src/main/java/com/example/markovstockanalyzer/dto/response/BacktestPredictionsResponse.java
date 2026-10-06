package com.example.markovstockanalyzer.dto.response;

import java.util.List;

public record BacktestPredictionsResponse(List<BacktestPrediction> items, int page, int size, long totalElements) {
}
