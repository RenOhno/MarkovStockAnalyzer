package com.example.markovstockanalyzer.service;

import com.example.markovstockanalyzer.model.BacktestResult;
import com.example.markovstockanalyzer.repository.BacktestResultRepository;
import org.springframework.stereotype.Service;

@Service
public class BacktestResultWriter {
    private final BacktestResultRepository results;
    public BacktestResultWriter(BacktestResultRepository results) { this.results = results; }
    public BacktestResult save(BacktestExecutionResult execution) {
        return results.save(execution.condition().id(), execution.dataset().id(), execution.evaluation(),
                execution.calculatedBacktest());
    }
}
