package com.example.markovstockanalyzer.persistence.adapter;

import com.example.markovstockanalyzer.dto.request.BacktestEvaluation;
import com.example.markovstockanalyzer.dto.response.*;
import com.example.markovstockanalyzer.model.BacktestResult;
import com.example.markovstockanalyzer.persistence.entity.*;
import com.example.markovstockanalyzer.persistence.repository.*;
import com.example.markovstockanalyzer.repository.BacktestResultRepository;
import com.example.markovstockanalyzer.exception.CalculationInvariantFailedException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Repository
@Profile("mysql")
@Transactional(readOnly = true)
public class JpaBacktestResultRepositoryAdapter implements BacktestResultRepository {
    private final BacktestResultJpaRepository results;
    private final AnalysisConditionJpaRepository conditions;
    private final PriceDatasetJpaRepository datasets;
    private final PersistenceJson json;
    public JpaBacktestResultRepositoryAdapter(BacktestResultJpaRepository results, AnalysisConditionJpaRepository conditions,
            PriceDatasetJpaRepository datasets, PersistenceJson json) {
        this.results = results; this.conditions = conditions; this.datasets = datasets; this.json = json;
    }
    @Transactional
    public BacktestResult save(Long conditionId, Long datasetId, BacktestEvaluation evaluation, CalculatedBacktest calculated) {
        if (calculated == null || calculated.summary() == null || calculated.predictions() == null
                || !Objects.equals(calculated.predictions().size(), calculated.summary().eligibleCount())) {
            throw new CalculationInvariantFailedException("Backtest predictions must equal eligibleCount");
        }
        var summary = calculated.summary(); var entity = new BacktestResultEntity();
        entity.setCondition(conditions.getReferenceById(conditionId)); entity.setDataset(datasets.getReferenceById(datasetId));
        entity.setTestStart(evaluation.testStart()); entity.setTestEnd(evaluation.testEnd()); entity.setHorizon(evaluation.horizon());
        entity.setMinTrainStates(evaluation.minTrainStates()); entity.setTrainingMode(evaluation.trainingMode()); entity.setWindowSize(evaluation.windowSize());
        entity.setEligibleCount(summary.eligibleCount()); entity.setPredictedCount(summary.predictedCount());
        entity.setCorrectCount(summary.correctCount()); entity.setSkippedCount(summary.skippedCount());
        entity.setMetrics(json.write(new Metrics(1, summary.coverage(), summary.metrics())));
        entity.setEngineVersion(calculated.engineVersion()); entity.setRuntime(json.write(calculated.runtime()));
        entity.setCreatedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        for (BacktestPrediction prediction : calculated.predictions()) {
            var child = new BacktestPredictionEntity(); child.setId(new BacktestPredictionId(null, prediction.targetDate()));
            child.setOriginDate(prediction.originDate()); child.setTrainStart(prediction.trainStart()); child.setTrainEnd(prediction.trainEnd());
            child.setActualState(prediction.actualState()); child.setPredictedState(prediction.predictedState());
            child.setProbabilities(prediction.probabilities() == null ? null : json.write(prediction.probabilities()));
            child.setMajorityState(prediction.majorityState()); child.setPersistenceState(prediction.persistenceState());
            child.setStatus(prediction.status()); child.setSkipCode(prediction.skipCode()); entity.addPrediction(child);
        }
        return domain(results.saveAndFlush(entity));
    }
    public Optional<BacktestResult> findById(Long id) { return results.findById(id).map(this::domain); }
    public List<BacktestResult> findAll() { return results.findAll().stream().map(this::domain).toList(); }
    private BacktestResult domain(BacktestResultEntity entity) {
        if (entity.getPredictions().size() != entity.getEligibleCount()) {
            throw new CalculationInvariantFailedException("Stored prediction count is invalid");
        }
        var predictions = entity.getPredictions().stream().sorted(Comparator.comparing(child -> child.getId().getTargetDate()))
                .map(child -> new BacktestPrediction(child.getOriginDate(), child.getId().getTargetDate(), child.getTrainStart(),
                        child.getTrainEnd(), child.getActualState(), child.getPredictedState(), child.getProbabilities() == null ? null :
                        json.read(child.getProbabilities(), new TypeReference<List<Double>>() {}), child.getMajorityState(),
                        child.getPersistenceState(), child.getStatus(), child.getSkipCode())).toList();
        var metrics = json.read(entity.getMetrics(), Metrics.class);
        var summary = new BacktestSummary(entity.getTestStart(), entity.getTestEnd(), entity.getHorizon(), entity.getEligibleCount(),
                entity.getPredictedCount(), entity.getCorrectCount(), entity.getSkippedCount(), metrics.coverage(), metrics.metrics());
        var calculated = new CalculatedBacktest(summary, predictions, entity.getEngineVersion(),
                json.read(entity.getRuntime(), new TypeReference<Map<String, Object>>() {}));
        return new BacktestResult(entity.getId(), entity.getCondition().getId(), entity.getDataset().getId(), entity.getTestStart(),
                entity.getTestEnd(), entity.getHorizon(), entity.getMinTrainStates(), entity.getTrainingMode(), entity.getWindowSize(),
                calculated, entity.getCreatedAt());
    }
    private record Metrics(Integer schemaVersion, Double coverage, BacktestMetrics metrics) {}
}
