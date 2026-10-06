package com.example.markovstockanalyzer.service;

import com.example.markovstockanalyzer.client.PythonAnalysisClient;
import com.example.markovstockanalyzer.dto.request.AnalysisCondition;
import com.example.markovstockanalyzer.dto.request.AnalyzeInput;
import com.example.markovstockanalyzer.dto.request.SeriesInput;
import com.example.markovstockanalyzer.dto.response.AnalysisSeriesResponse;
import com.example.markovstockanalyzer.dto.response.CalculatedSeries;
import com.example.markovstockanalyzer.dto.response.ConditionResponse;
import com.example.markovstockanalyzer.dto.response.SeriesPointResponse;
import com.example.markovstockanalyzer.exception.AnalysisResultNotFoundException;
import com.example.markovstockanalyzer.exception.ApiErrorResponse;
import com.example.markovstockanalyzer.exception.ConditionNotFoundException;
import com.example.markovstockanalyzer.exception.PriceDatasetNotFoundException;
import com.example.markovstockanalyzer.exception.PythonApiException;
import com.example.markovstockanalyzer.model.AnalysisResult;
import com.example.markovstockanalyzer.model.PriceDataset;
import com.example.markovstockanalyzer.repository.AnalysisResultRepository;
import com.example.markovstockanalyzer.repository.ConditionRepository;
import com.example.markovstockanalyzer.repository.PriceDatasetRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AnalysisSeriesService {
    private final AnalysisResultRepository results;
    private final ConditionRepository conditions;
    private final PriceDatasetRepository datasets;
    private final PythonAnalysisClient pythonClient;

    public AnalysisSeriesService(AnalysisResultRepository results, ConditionRepository conditions,
                                 PriceDatasetRepository datasets, PythonAnalysisClient pythonClient) {
        this.results = results;
        this.conditions = conditions;
        this.datasets = datasets;
        this.pythonClient = pythonClient;
    }

    public AnalysisSeriesResponse series(Long analysisId, String requestId) {
        AnalysisResult result = results.findById(analysisId)
                .orElseThrow(() -> new AnalysisResultNotFoundException(analysisId));
        ConditionResponse condition = conditions.findById(result.conditionId())
                .orElseThrow(() -> new ConditionNotFoundException(result.conditionId()));
        PriceDataset dataset = datasets.findById(result.datasetId())
                .orElseThrow(() -> new PriceDatasetNotFoundException(result.datasetId()));
        String requiredEngineVersion = result.calculatedAnalysis().engineVersion();
        SeriesInput input = new SeriesInput(
                new AnalyzeInput(requestId, AnalysisCondition.from(condition), dataset.dataset()), requiredEngineVersion);
        CalculatedSeries calculated = pythonClient.series(input);
        if (requiredEngineVersion == null || !requiredEngineVersion.equals(calculated.engineVersion())) {
            throw new PythonApiException(409, new ApiErrorResponse(
                    "ENGINE_VERSION_UNSUPPORTED", "Saved engine version cannot be reproduced", requestId, Map.of()), null);
        }
        return new AnalysisSeriesResponse(result.id().toString(), calculated.priceBasis(), calculated.points().stream()
                .map(point -> new SeriesPointResponse(point.date(), point.close(), point.adjustedClose(),
                        point.returnValue(), point.state()))
                .toList());
    }
}
