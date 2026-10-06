package com.example.markovstockanalyzer.service;

import com.example.markovstockanalyzer.client.PythonAnalysisClient;
import com.example.markovstockanalyzer.dto.request.AnalysisCondition;
import com.example.markovstockanalyzer.dto.request.AnalyzeInput;
import com.example.markovstockanalyzer.dto.request.FetchPricesRequest;
import com.example.markovstockanalyzer.dto.response.CalculatedAnalysis;
import com.example.markovstockanalyzer.dto.response.ConditionResponse;
import com.example.markovstockanalyzer.dto.response.PriceDatasetPayload;
import com.example.markovstockanalyzer.dto.response.StockSummary;
import com.example.markovstockanalyzer.exception.StockNotFoundException;
import com.example.markovstockanalyzer.repository.StockRepository;
import com.example.markovstockanalyzer.validation.AnalysisResultValidator;
import org.springframework.stereotype.Service;

@Service
public class AnalysisService {
    private final ConditionService conditionService;
    private final StockRepository stockRepository;
    private final PythonAnalysisClient pythonAnalysisClient;
    private final AnalysisResultValidator resultValidator;

    public AnalysisService(
            ConditionService conditionService,
            StockRepository stockRepository,
            PythonAnalysisClient pythonAnalysisClient,
            AnalysisResultValidator resultValidator
    ) {
        this.conditionService = conditionService;
        this.stockRepository = stockRepository;
        this.pythonAnalysisClient = pythonAnalysisClient;
        this.resultValidator = resultValidator;
    }

    public CalculatedAnalysis analyze(Long conditionId, String requestId) {
        ConditionResponse condition = conditionService.findById(conditionId);
        StockSummary stock = stockRepository.findAll().stream()
                .filter(candidate -> candidate.id().equals(condition.stockId()))
                .findFirst()
                .orElseThrow(() -> new StockNotFoundException(condition.stockId()));

        FetchPricesRequest fetchRequest = new FetchPricesRequest(
                requestId, stock.ticker(), stock.exchange(), stock.timeZone(),
                condition.startDate(), condition.endDate(), true,
                "PROVIDER_ADJUSTED_CLOSE", "YFINANCE"
        );
        PriceDatasetPayload dataset = pythonAnalysisClient.fetchPrices(fetchRequest);
        AnalyzeInput input = new AnalyzeInput(requestId, AnalysisCondition.from(condition), dataset);
        CalculatedAnalysis calculated = pythonAnalysisClient.analyze(input);
        resultValidator.validate(calculated, input.engineVersion());
        return calculated;
    }
}
