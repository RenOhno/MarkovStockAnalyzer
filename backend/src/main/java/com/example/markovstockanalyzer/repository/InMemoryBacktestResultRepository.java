package com.example.markovstockanalyzer.repository;

import com.example.markovstockanalyzer.dto.request.BacktestEvaluation;
import com.example.markovstockanalyzer.dto.response.CalculatedBacktest;
import com.example.markovstockanalyzer.model.BacktestResult;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryBacktestResultRepository implements BacktestResultRepository {
    private final AtomicLong sequence = new AtomicLong();
    private final ConcurrentHashMap<Long, BacktestResult> results = new ConcurrentHashMap<>();

    @Override
    public BacktestResult save(Long conditionId, Long datasetId, BacktestEvaluation evaluation, CalculatedBacktest calculated) {
        java.util.Objects.requireNonNull(conditionId, "conditionId");
        java.util.Objects.requireNonNull(datasetId, "datasetId");
        CalculatedBacktest snapshot = InMemorySnapshots.backtest(calculated);
        long id = sequence.incrementAndGet();
        BacktestResult saved = new BacktestResult(id, conditionId, datasetId, evaluation.testStart(), evaluation.testEnd(),
                evaluation.horizon(), evaluation.minTrainStates(), evaluation.trainingMode(), evaluation.windowSize(),
                snapshot, Instant.now());
        results.put(id, saved);
        return saved;
    }

    @Override
    public Optional<BacktestResult> findById(Long id) {
        return Optional.ofNullable(results.get(id));
    }
}
