package com.example.markovstockanalyzer.persistence;

import com.example.markovstockanalyzer.model.AnalysisResult;
import com.example.markovstockanalyzer.repository.AnalysisResultRepository;
import com.example.markovstockanalyzer.repository.PriceDatasetRepository;
import com.example.markovstockanalyzer.service.AnalysisExecutionResult;
import com.example.markovstockanalyzer.service.AnalysisResultWriter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("mysql")
public class TransactionalAnalysisResultWriter extends AnalysisResultWriter {
    public TransactionalAnalysisResultWriter(PriceDatasetRepository datasets, AnalysisResultRepository results) { super(datasets, results); }
    @Override @Transactional
    public AnalysisResult save(AnalysisExecutionResult execution) { return super.save(execution, execution.datasetId()); }
    @Override @Transactional
    public AnalysisResult save(AnalysisExecutionResult execution, Long datasetId) { return super.save(execution, datasetId); }
}
