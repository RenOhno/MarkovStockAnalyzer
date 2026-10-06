package com.example.markovstockanalyzer.mapper;

import com.example.markovstockanalyzer.dto.response.BacktestResultResponse;
import com.example.markovstockanalyzer.model.BacktestResult;
import com.example.markovstockanalyzer.model.PriceDataset;
import org.springframework.stereotype.Component;

@Component
public class BacktestResultResponseMapper {
    private final AnalysisResultResponseMapper metadata;
    public BacktestResultResponseMapper(AnalysisResultResponseMapper metadata) { this.metadata = metadata; }

    public BacktestResultResponse map(BacktestResult result, PriceDataset dataset) {
        if (!result.datasetId().equals(dataset.id())) {
            throw new IllegalArgumentException("PriceDataset id does not match backtest datasetId");
        }
        var calculated = result.calculatedBacktest();
        var summary = calculated.summary();
        return new BacktestResultResponse(result.id().toString(), result.conditionId().toString(), result.datasetId().toString(),
                result.testStart(), result.testEnd(), result.horizon(), summary.eligibleCount(), summary.predictedCount(),
                summary.correctCount(), summary.skippedCount(), summary.coverage(), summary.metrics(), calculated.engineVersion(),
                metadata.dataSource(dataset), metadata.provenance(calculated.runtime()), result.createdAt());
    }
}
