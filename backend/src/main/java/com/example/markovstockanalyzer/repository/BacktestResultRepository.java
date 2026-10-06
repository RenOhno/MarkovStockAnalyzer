package com.example.markovstockanalyzer.repository;

import com.example.markovstockanalyzer.dto.request.BacktestEvaluation;
import com.example.markovstockanalyzer.dto.response.CalculatedBacktest;
import com.example.markovstockanalyzer.model.BacktestResult;
import java.util.Optional;

public interface BacktestResultRepository {
    BacktestResult save(Long conditionId, Long datasetId, BacktestEvaluation evaluation, CalculatedBacktest calculated);
    Optional<BacktestResult> findById(Long id);
}
