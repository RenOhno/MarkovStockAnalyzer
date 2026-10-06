package com.example.markovstockanalyzer.repository;

import com.example.markovstockanalyzer.dto.response.CalculatedAnalysis;
import com.example.markovstockanalyzer.model.AnalysisResult;

import java.util.Optional;
import java.util.List;

public interface AnalysisResultRepository {
    AnalysisResult save(Long conditionId, Long datasetId, CalculatedAnalysis calculatedAnalysis);

    Optional<AnalysisResult> findById(Long id);

    List<AnalysisResult> findAll();
}
