package com.example.markovstockanalyzer.service;

import com.example.markovstockanalyzer.client.PythonAnalysisClient;
import com.example.markovstockanalyzer.dto.request.AnalysisCondition;
import com.example.markovstockanalyzer.dto.request.AnalyzeInput;
import com.example.markovstockanalyzer.dto.request.FetchPricesRequest;
import com.example.markovstockanalyzer.dto.response.CalculatedAnalysis;
import com.example.markovstockanalyzer.dto.response.ConditionResponse;
import com.example.markovstockanalyzer.dto.response.PriceDatasetPayload;
import com.example.markovstockanalyzer.dto.response.StockSummary;
import com.example.markovstockanalyzer.exception.DatasetConditionMismatchException;
import com.example.markovstockanalyzer.exception.PriceDatasetNotFoundException;
import com.example.markovstockanalyzer.exception.StockNotFoundException;
import com.example.markovstockanalyzer.model.PriceDataset;
import com.example.markovstockanalyzer.repository.PriceDatasetRepository;
import com.example.markovstockanalyzer.repository.StockRepository;
import com.example.markovstockanalyzer.validation.AnalysisResultValidator;
import org.springframework.stereotype.Service;

@Service
public class AnalysisService {
    private final ConditionService conditionService;
    private final StockRepository stockRepository;
    private final PriceDatasetRepository priceDatasetRepository;
    private final PythonAnalysisClient pythonAnalysisClient;
    private final AnalysisResultValidator resultValidator;

    public AnalysisService(
            ConditionService conditionService,
            StockRepository stockRepository,
            PriceDatasetRepository priceDatasetRepository,
            PythonAnalysisClient pythonAnalysisClient,
            AnalysisResultValidator resultValidator
    ) {
        this.conditionService = conditionService;
        this.stockRepository = stockRepository;
        this.priceDatasetRepository = priceDatasetRepository;
        this.pythonAnalysisClient = pythonAnalysisClient;
        this.resultValidator = resultValidator;
    }

    public AnalysisExecutionResult analyze(Long conditionId, String requestId) {
        return analyze(conditionId, null, requestId);
    }

    public AnalysisExecutionResult analyze(Long conditionId, Long datasetId, String requestId) {
        ConditionResponse condition = conditionService.findById(conditionId);
        PriceDatasetPayload dataset = datasetId == null
                ? fetchDataset(condition, requestId)
                : reuseDataset(condition, datasetId);
        AnalyzeInput input = new AnalyzeInput(requestId, AnalysisCondition.from(condition), dataset);
        CalculatedAnalysis calculated = pythonAnalysisClient.analyze(input);
        resultValidator.validate(calculated, input.engineVersion());
        return new AnalysisExecutionResult(condition, dataset, calculated);
    }

    private PriceDatasetPayload fetchDataset(ConditionResponse condition, String requestId) {
        StockSummary stock = stockRepository.findAll().stream()
                .filter(candidate -> candidate.id().equals(condition.stockId()))
                .findFirst()
                .orElseThrow(() -> new StockNotFoundException(condition.stockId()));

        FetchPricesRequest fetchRequest = new FetchPricesRequest(
                requestId, stock.ticker(), stock.exchange(), stock.timeZone(),
                condition.startDate(), condition.endDate(), true,
                "PROVIDER_ADJUSTED_CLOSE", "YFINANCE"
        );
        return pythonAnalysisClient.fetchPrices(fetchRequest);
    }

    private PriceDatasetPayload reuseDataset(ConditionResponse condition, Long datasetId) {
        PriceDataset saved = priceDatasetRepository.findById(datasetId)
                .orElseThrow(() -> new PriceDatasetNotFoundException(datasetId));
        if (!condition.stockId().equals(saved.stockId())) {
            throw new DatasetConditionMismatchException("Dataset stockId does not match condition");
        }
        PriceDatasetPayload dataset = saved.dataset();
        if (dataset == null || !"PROVIDER_ADJUSTED_CLOSE".equals(dataset.priceBasis())) {
            throw new DatasetConditionMismatchException("Dataset priceBasis does not match condition");
        }
        // Calendar bounds are inclusive calendar dates (design section 10.2).
        // Python's existing XTKS validation checks exact sessions and the preceding session;
        // do not reject a valid weekend/holiday end date by comparing coverageEnd to endDate.
        if (dataset.coverageStart() == null || dataset.coverageEnd() == null
                || !dataset.coverageStart().isBefore(condition.startDate())
                || dataset.coverageEnd().isBefore(condition.startDate())
                || dataset.prices() == null
                || dataset.prices().stream().noneMatch(price -> price != null && price.date() != null
                        && price.date().isBefore(condition.startDate()))
                || dataset.prices().stream().noneMatch(price -> price != null && price.date() != null
                        && !price.date().isBefore(condition.startDate())
                        && !price.date().isAfter(condition.endDate()))) {
            throw new DatasetConditionMismatchException("Dataset must include the condition range and preceding price");
        }
        return dataset;
    }
}
