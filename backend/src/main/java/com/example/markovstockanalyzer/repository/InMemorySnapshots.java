package com.example.markovstockanalyzer.repository;

import com.example.markovstockanalyzer.dto.response.AnalysisWarning;
import com.example.markovstockanalyzer.dto.response.CalculatedAnalysis;
import com.example.markovstockanalyzer.dto.response.ForecastPayload;
import com.example.markovstockanalyzer.dto.response.PriceDatasetPayload;
import com.example.markovstockanalyzer.dto.response.BacktestMetrics;
import com.example.markovstockanalyzer.dto.response.BacktestPrediction;
import com.example.markovstockanalyzer.dto.response.BacktestSummary;
import com.example.markovstockanalyzer.dto.response.CalculatedBacktest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Keep saved snapshots independent of mutable DTO collections, including nested JSON values.
final class InMemorySnapshots {
    private InMemorySnapshots() {
    }

    static PriceDatasetPayload dataset(PriceDatasetPayload source) {
        return new PriceDatasetPayload(
                source.ticker(), source.exchange(), source.timeZone(), source.priceBasis(), source.provider(),
                source.providerVersion(), source.adjustmentPolicy(), source.fetchedAt(),
                source.coverageStart(), source.coverageEnd(), source.contentSha256(),
                jsonMap(source.metadata()), list(source.prices())
        );
    }

    static CalculatedAnalysis analysis(CalculatedAnalysis source) {
        return new CalculatedAnalysis(
                list(source.stateOrder()), source.asOfDate(), source.currentState(), source.sampleCount(),
                source.transitionCount(), source.transitionCounts().stream().map(InMemorySnapshots::list).toList(),
                source.transitionMatrix().stream().map(InMemorySnapshots::list).toList(), source.predictionStatus(),
                source.forecasts().stream()
                        .map(forecast -> new ForecastPayload(forecast.horizon(), list(forecast.probabilities())))
                        .toList(),
                source.warnings().stream()
                        .map(warning -> new AnalysisWarning(warning.code(), list(warning.states())))
                        .toList(),
                source.engineVersion(), jsonMap(source.runtime())
        );
    }

    static CalculatedBacktest backtest(CalculatedBacktest source) {
        BacktestMetrics metrics = source.summary().metrics();
        BacktestMetrics copiedMetrics = new BacktestMetrics(metrics.accuracy(), list(metrics.precision()), list(metrics.recall()),
                metrics.confusionMatrix().stream().map(InMemorySnapshots::list).toList(), metrics.brierScore(), metrics.logLoss(),
                metrics.majorityAccuracy(), metrics.persistenceAccuracy(), metrics.tieCount(), Map.copyOf(metrics.skipReasons()));
        BacktestSummary summary = source.summary();
        BacktestSummary copiedSummary = new BacktestSummary(summary.testStart(), summary.testEnd(), summary.horizon(),
                summary.eligibleCount(), summary.predictedCount(), summary.correctCount(), summary.skippedCount(),
                summary.coverage(), copiedMetrics);
        return new CalculatedBacktest(copiedSummary, source.predictions().stream().map(prediction -> new BacktestPrediction(
                prediction.originDate(), prediction.targetDate(), prediction.trainStart(), prediction.trainEnd(),
                prediction.actualState(), prediction.predictedState(),
                prediction.probabilities() == null ? null : list(prediction.probabilities()), prediction.majorityState(),
                prediction.persistenceState(), prediction.status(), prediction.skipCode())).toList(),
                source.engineVersion(), jsonMap(source.runtime()));
    }

    private static <T> List<T> list(List<T> source) {
        // List.copyOf rejects null cells, which are valid in unestimated probability rows.
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    private static <K> Map<K, Object> jsonMap(Map<K, ?> source) {
        Map<K, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, jsonValue(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object jsonValue(Object source) {
        if (source instanceof Map<?, ?> map) {
            return jsonMap(map);
        }
        if (source instanceof List<?> list) {
            return list.stream().map(InMemorySnapshots::jsonValue).toList();
        }
        return source;
    }
}
