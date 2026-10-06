package com.example.markovstockanalyzer.service;

import com.example.markovstockanalyzer.model.AnalysisResult;
import com.example.markovstockanalyzer.model.PriceDataset;
import com.example.markovstockanalyzer.repository.AnalysisResultRepository;
import com.example.markovstockanalyzer.repository.PriceDatasetRepository;
import org.springframework.stereotype.Service;

@Service
public class AnalysisResultWriter {
    private final PriceDatasetRepository priceDatasetRepository;
    private final AnalysisResultRepository analysisResultRepository;

    public AnalysisResultWriter(
            PriceDatasetRepository priceDatasetRepository,
            AnalysisResultRepository analysisResultRepository
    ) {
        this.priceDatasetRepository = priceDatasetRepository;
        this.analysisResultRepository = analysisResultRepository;
    }

    public AnalysisResult save(AnalysisExecutionResult execution) {
        PriceDataset dataset = priceDatasetRepository.save(execution.condition().stockId(), execution.dataset());
        return analysisResultRepository.save(
                execution.condition().id(), dataset.id(), execution.calculatedAnalysis()
        );
    }
}
