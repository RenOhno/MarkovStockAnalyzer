package com.example.markovstockanalyzer.service;

import com.example.markovstockanalyzer.client.PythonAnalysisClient;
import com.example.markovstockanalyzer.dto.request.*;
import com.example.markovstockanalyzer.dto.response.*;
import com.example.markovstockanalyzer.exception.*;
import com.example.markovstockanalyzer.model.PriceDataset;
import com.example.markovstockanalyzer.repository.ConditionRepository;
import com.example.markovstockanalyzer.repository.PriceDatasetRepository;
import com.example.markovstockanalyzer.validation.BacktestResultValidator;
import com.example.markovstockanalyzer.validation.ConditionDatasetValidator;
import org.springframework.stereotype.Service;

@Service
public class BacktestService {
    private final ConditionRepository conditions;
    private final PriceDatasetRepository datasets;
    private final PythonAnalysisClient pythonClient;
    private final BacktestResultValidator validator;

    public BacktestService(ConditionRepository conditions, PriceDatasetRepository datasets,
                           PythonAnalysisClient pythonClient, BacktestResultValidator validator) {
        this.conditions = conditions;
        this.datasets = datasets;
        this.pythonClient = pythonClient;
        this.validator = validator;
    }

    public BacktestExecutionResult backtest(CreateBacktestRequest request, String requestId) {
        ConditionResponse condition = conditions.findById(request.conditionId())
                .orElseThrow(() -> new ConditionNotFoundException(request.conditionId()));
        PriceDataset dataset = datasets.findById(request.datasetId())
                .orElseThrow(() -> new PriceDatasetNotFoundException(request.datasetId()));
        if (request.testStart() == null || request.testEnd() == null
                || request.testStart().isAfter(request.testEnd())
                || request.testStart().isBefore(condition.startDate())
                || request.testEnd().isAfter(condition.endDate())
                || request.minTrainStates() == null || request.minTrainStates() < 30
                || !"EXPANDING".equals(request.trainingMode()) || request.windowSize() != null
                || !Integer.valueOf(1).equals(request.horizon())) {
            throw new InvalidBacktestRequestException("Backtest evaluation is outside the supported range");
        }
        ConditionDatasetValidator.validate(condition, dataset);
        BacktestInput input = new BacktestInput(
                new AnalyzeInput(requestId, AnalysisCondition.from(condition), dataset.dataset()), request.evaluation());
        CalculatedBacktest calculated = pythonClient.backtest(input);
        validator.validate(calculated, input);
        return new BacktestExecutionResult(condition, dataset, input.evaluation(), calculated);
    }
}
