package com.example.markovstockanalyzer.model;

import com.example.markovstockanalyzer.dto.response.CalculatedBacktest;
import java.time.Instant;
import java.time.LocalDate;

public record BacktestResult(
        Long id, Long conditionId, Long datasetId, LocalDate testStart, LocalDate testEnd,
        Integer horizon, Integer minTrainStates, String trainingMode, Integer windowSize,
        CalculatedBacktest calculatedBacktest, Instant createdAt
) {
}
