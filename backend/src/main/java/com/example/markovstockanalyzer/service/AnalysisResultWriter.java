package com.example.markovstockanalyzer.service;

import com.example.markovstockanalyzer.model.AnalysisResult;
import com.example.markovstockanalyzer.model.PriceDataset;
import com.example.markovstockanalyzer.exception.PriceDatasetNotFoundException;
import com.example.markovstockanalyzer.repository.AnalysisResultRepository;
import com.example.markovstockanalyzer.repository.PriceDatasetRepository;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;

@Service
@Profile("!mysql")
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
        return save(execution, null);
    }

    public AnalysisResult save(AnalysisExecutionResult execution, Long datasetId) {
        if (execution.datasetId() != null) { datasetId = execution.datasetId(); }
        final Long selectedDatasetId = datasetId;
        PriceDataset dataset = datasetId == null
                ? priceDatasetRepository.save(execution.condition().stockId(), execution.dataset())
                : priceDatasetRepository.findById(datasetId)
                        .orElseThrow(() -> new PriceDatasetNotFoundException(selectedDatasetId));
        return analysisResultRepository.save(
                execution.condition().id(), dataset.id(), execution.calculatedAnalysis()
        );
    }
}
