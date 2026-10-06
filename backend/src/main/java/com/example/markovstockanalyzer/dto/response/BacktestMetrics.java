package com.example.markovstockanalyzer.dto.response;

import java.util.List;
import java.util.Map;

public record BacktestMetrics(Double accuracy, List<Double> precision, List<Double> recall,
                              List<List<Integer>> confusionMatrix, Double brierScore, Double logLoss,
                              Double majorityAccuracy, Double persistenceAccuracy,
                              Integer tieCount, Map<String, Integer> skipReasons) {
}
