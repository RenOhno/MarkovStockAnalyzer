package com.example.markovstockanalyzer.service;

import com.example.markovstockanalyzer.dto.response.BacktestResultResponse;
import com.example.markovstockanalyzer.dto.response.BacktestPredictionsResponse;
import com.example.markovstockanalyzer.exception.*;
import com.example.markovstockanalyzer.mapper.BacktestResultResponseMapper;
import com.example.markovstockanalyzer.model.BacktestResult;
import com.example.markovstockanalyzer.repository.BacktestResultRepository;
import com.example.markovstockanalyzer.repository.PriceDatasetRepository;
import org.springframework.stereotype.Service;

@Service
public class BacktestResultQueryService {
    private final BacktestResultRepository results;
    private final PriceDatasetRepository datasets;
    private final BacktestResultResponseMapper mapper;

    public BacktestResultQueryService(BacktestResultRepository results, PriceDatasetRepository datasets,
                                      BacktestResultResponseMapper mapper) {
        this.results = results;
        this.datasets = datasets;
        this.mapper = mapper;
    }

    public BacktestResultResponse findById(Long id) {
        BacktestResult result = result(id);
        var dataset = datasets.findById(result.datasetId())
                .orElseThrow(() -> new PriceDatasetNotFoundException(result.datasetId()));
        return mapper.map(result, dataset);
    }

    public BacktestPredictionsResponse predictions(Long id, int page, int size) {
        if (page < 0 || size < 1 || size > 100) { throw new InvalidPaginationException(); }
        var predictions = result(id).calculatedBacktest().predictions();
        long offset = (long) page * size;
        int from = (int) Math.min(offset, predictions.size());
        int to = Math.min(from + size, predictions.size());
        return new BacktestPredictionsResponse(predictions.subList(from, to), page, size, predictions.size());
    }

    private BacktestResult result(Long id) {
        return results.findById(id).orElseThrow(() -> new BacktestResultNotFoundException(id));
    }
}
