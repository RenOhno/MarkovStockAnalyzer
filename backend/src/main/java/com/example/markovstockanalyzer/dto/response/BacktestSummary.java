package com.example.markovstockanalyzer.dto.response;

import java.time.LocalDate;

public record BacktestSummary(LocalDate testStart, LocalDate testEnd, Integer horizon,
                              Integer eligibleCount, Integer predictedCount, Integer correctCount,
                              Integer skippedCount, Double coverage, BacktestMetrics metrics) {
}
