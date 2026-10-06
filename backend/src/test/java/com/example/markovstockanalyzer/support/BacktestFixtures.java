package com.example.markovstockanalyzer.support;

import com.example.markovstockanalyzer.dto.request.*;
import com.example.markovstockanalyzer.dto.response.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class BacktestFixtures {
    private BacktestFixtures() {}
    public static final ConditionResponse CONDITION = new ConditionResponse(101L, "Backtest fixture", "7203",
            LocalDate.parse("2025-01-06"), LocalDate.parse("2025-02-28"), new BigDecimal("-0.005"),
            new BigDecimal("0.005"), 3, "MLE_STRICT", "FULL", null, List.of(1, 3, 5, 10), Instant.parse("2025-01-01T00:00:00Z"));
    public static final CreateBacktestRequest REQUEST = request(501L);
    public static CreateBacktestRequest request(Long datasetId) {
        return new CreateBacktestRequest(101L, datasetId, LocalDate.parse("2025-02-20"), LocalDate.parse("2025-02-21"),
                30, "EXPANDING", null, 1);
    }
    public static BacktestInput input() {
        return new BacktestInput(new AnalyzeInput("backtest-request-001", AnalysisCondition.from(CONDITION), dataset()),
                REQUEST.evaluation());
    }
    public static PriceDatasetPayload dataset() {
        return new PriceDatasetPayload("7203.T", "XTKS", "Asia/Tokyo", "PROVIDER_ADJUSTED_CLOSE", "YFINANCE", "0.2.65",
                "PROVIDER_ADJUSTED_CLOSE_V1", OffsetDateTime.parse("2026-10-06T01:02:03Z"), LocalDate.parse("2024-12-30"),
                CONDITION.endDate(), "a".repeat(64), Map.ofEntries(
                Map.entry("schemaVersion", 1), Map.entry("normalizationVersion", "NORMALIZATION_V1"),
                Map.entry("calendarName", "XTKS"), Map.entry("calendarVersion", "4.11.1"), Map.entry("roundingMode", "ROUND_HALF_UP"),
                Map.entry("scale", 10), Map.entry("fetchOptions", Map.of("interval", "1d", "auto_adjust", false,
                        "actions", true, "repair", false, "rounding", false)),
                Map.entry("qualityFlags", List.of("SYNTHETIC_FIXTURE")), Map.entry("ticker", "7203.T"), Map.entry("exchange", "XTKS"),
                Map.entry("timeZone", "Asia/Tokyo"), Map.entry("priceBasis", "PROVIDER_ADJUSTED_CLOSE"),
                Map.entry("provider", "YFINANCE"), Map.entry("providerVersion", "0.2.65")),
                List.of("2024-12-30", "2025-01-06", "2025-02-19", "2025-02-20", "2025-02-21", "2025-02-28").stream()
                        .map(date -> new PricePoint(LocalDate.parse(date), "100.0000000000", "99.0000000000", null)).toList());
    }
    public static BacktestPrediction scored() {
        return new BacktestPrediction(LocalDate.parse("2025-02-19"), REQUEST.testStart(), CONDITION.startDate(),
                LocalDate.parse("2025-02-19"), "UP", "UP", List.of(0.9, 0.05, 0.05), "UP", "FLAT", "SCORED", null);
    }
    public static BacktestPrediction skipped() {
        return new BacktestPrediction(REQUEST.testStart(), REQUEST.testEnd(), CONDITION.startDate(), REQUEST.testStart(),
                "DOWN", null, null, null, null, "SKIPPED", "ZERO_ROW_UNESTIMATED");
    }
    public static Map<String, Object> runtime() {
        return new LinkedHashMap<>(Map.of("engineVersion", "msa-core-v1", "gitCommit", "b".repeat(40),
                "numpyVersion", "2.2.0", "normalizationVersion", "NORMALIZATION_V1", "configurationVersion", "analysis-config-v1",
                "INTERNAL_API_TOKEN", "runtime-secret", "internalPath", "/private/analysis"));
    }
    public static CalculatedBacktest mixed() {
        BacktestMetrics metrics = new BacktestMetrics(1.0, Arrays.asList(1.0, null, null), Arrays.asList(1.0, null, null),
                List.of(List.of(1, 0, 0), List.of(0, 0, 0), List.of(0, 0, 0)), 0.015, 0.10536051565782628,
                1.0, 0.0, 0, Map.of("ZERO_ROW_UNESTIMATED", 1));
        return new CalculatedBacktest(new BacktestSummary(REQUEST.testStart(), REQUEST.testEnd(), 1, 2, 1, 1, 1, 0.5, metrics),
                List.of(scored(), skipped()), "msa-core-v1", runtime());
    }
    public static CalculatedBacktest allSkipped() {
        BacktestPrediction first = scored();
        BacktestPrediction skipFirst = new BacktestPrediction(first.originDate(), first.targetDate(), first.trainStart(), first.trainEnd(),
                first.actualState(), null, null, null, null, "SKIPPED", "ZERO_ROW_UNESTIMATED");
        BacktestMetrics metrics = new BacktestMetrics(null, Arrays.asList(null, null, null), Arrays.asList(null, null, null),
                List.of(List.of(0, 0, 0), List.of(0, 0, 0), List.of(0, 0, 0)), null, null, null, null, 0,
                Map.of("ZERO_ROW_UNESTIMATED", 2));
        return new CalculatedBacktest(new BacktestSummary(REQUEST.testStart(), REQUEST.testEnd(), 1, 2, 0, 0, 2, 0.0, metrics),
                List.of(skipFirst, skipped()), "msa-core-v1", runtime());
    }
}
