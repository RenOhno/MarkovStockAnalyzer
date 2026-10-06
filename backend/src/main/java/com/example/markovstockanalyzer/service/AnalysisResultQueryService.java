package com.example.markovstockanalyzer.service;

import com.example.markovstockanalyzer.dto.response.AnalysisResultResponse;
import com.example.markovstockanalyzer.exception.AnalysisResultNotFoundException;
import com.example.markovstockanalyzer.exception.PriceDatasetNotFoundException;
import com.example.markovstockanalyzer.mapper.AnalysisResultResponseMapper;
import com.example.markovstockanalyzer.model.AnalysisResult;
import com.example.markovstockanalyzer.model.PriceDataset;
import com.example.markovstockanalyzer.repository.AnalysisResultRepository;
import com.example.markovstockanalyzer.repository.PriceDatasetRepository;
import org.springframework.stereotype.Service;

@Service
public class AnalysisResultQueryService {
    private final AnalysisResultRepository results;
    private final PriceDatasetRepository datasets;
    private final AnalysisResultResponseMapper mapper;

    public AnalysisResultQueryService(AnalysisResultRepository results, PriceDatasetRepository datasets,
                                      AnalysisResultResponseMapper mapper) {
        this.results = results;
        this.datasets = datasets;
        this.mapper = mapper;
    }

    public AnalysisResultResponse findById(Long analysisId) {
        AnalysisResult result = results.findById(analysisId)
                .orElseThrow(() -> new AnalysisResultNotFoundException(analysisId));
        PriceDataset dataset = datasets.findById(result.datasetId())
                .orElseThrow(() -> new PriceDatasetNotFoundException(result.datasetId()));
        return mapper.map(result, dataset);
    }
}
