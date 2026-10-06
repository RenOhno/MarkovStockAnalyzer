package com.example.markovstockanalyzer.service;

import com.example.markovstockanalyzer.dto.request.BacktestEvaluation;
import com.example.markovstockanalyzer.dto.response.CalculatedBacktest;
import com.example.markovstockanalyzer.dto.response.ConditionResponse;
import com.example.markovstockanalyzer.model.PriceDataset;

public record BacktestExecutionResult(ConditionResponse condition, PriceDataset dataset,
                                      BacktestEvaluation evaluation, CalculatedBacktest calculatedBacktest) {
}
