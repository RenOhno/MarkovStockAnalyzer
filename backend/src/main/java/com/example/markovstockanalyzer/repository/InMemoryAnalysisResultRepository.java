package com.example.markovstockanalyzer.repository;

import com.example.markovstockanalyzer.dto.response.CalculatedAnalysis;
import com.example.markovstockanalyzer.model.AnalysisResult;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryAnalysisResultRepository implements AnalysisResultRepository {
    private final AtomicLong sequence = new AtomicLong();
    private final ConcurrentHashMap<Long, AnalysisResult> results = new ConcurrentHashMap<>();

    @Override
    public AnalysisResult save(Long conditionId, Long datasetId, CalculatedAnalysis calculatedAnalysis) {
        Objects.requireNonNull(conditionId, "conditionId");
        Objects.requireNonNull(datasetId, "datasetId");
        CalculatedAnalysis snapshot = InMemorySnapshots.analysis(calculatedAnalysis);
        long id = sequence.incrementAndGet();
        AnalysisResult saved = new AnalysisResult(id, conditionId, datasetId, snapshot, Instant.now());
        results.put(id, saved);
        return saved;
    }

    @Override
    public List<AnalysisResult> findAll() {
        return List.copyOf(results.values());
    }

    @Override
    public Optional<AnalysisResult> findById(Long id) {
        return Optional.ofNullable(results.get(id));
    }
}
