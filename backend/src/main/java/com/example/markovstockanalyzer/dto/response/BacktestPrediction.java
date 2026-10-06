package com.example.markovstockanalyzer.dto.response;

import java.time.LocalDate;
import java.util.List;

public record BacktestPrediction(LocalDate originDate, LocalDate targetDate, LocalDate trainStart,
                                 LocalDate trainEnd, String actualState, String predictedState,
                                 List<Double> probabilities, String majorityState, String persistenceState,
                                 String status, String skipCode) {
}
