package com.example.markovstockanalyzer.dto.request;

import java.time.LocalDate;

public record BacktestEvaluation(LocalDate testStart, LocalDate testEnd, Integer minTrainStates,
                                 String trainingMode, Integer windowSize, Integer horizon) {
}
