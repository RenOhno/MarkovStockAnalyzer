package com.example.markovstockanalyzer.persistence.adapter;

import com.example.markovstockanalyzer.dto.response.*;
import com.example.markovstockanalyzer.model.AnalysisResult;
import com.example.markovstockanalyzer.persistence.entity.*;
import com.example.markovstockanalyzer.persistence.repository.*;
import com.example.markovstockanalyzer.repository.AnalysisResultRepository;
import com.example.markovstockanalyzer.validation.AnalysisResultValidator;
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
public class JpaAnalysisResultRepositoryAdapter implements AnalysisResultRepository {
    private static final List<String> STATES = List.of("UP", "FLAT", "DOWN");
    private final AnalysisResultJpaRepository results;
    private final AnalysisConditionJpaRepository conditions;
    private final PriceDatasetJpaRepository datasets;
    private final PersistenceJson json;
    private final AnalysisResultValidator validator;
    public JpaAnalysisResultRepositoryAdapter(AnalysisResultJpaRepository results, AnalysisConditionJpaRepository conditions,
            PriceDatasetJpaRepository datasets, PersistenceJson json, AnalysisResultValidator validator) {
        this.results = results; this.conditions = conditions; this.datasets = datasets; this.json = json; this.validator = validator;
    }
    @Transactional
    public AnalysisResult save(Long conditionId, Long datasetId, CalculatedAnalysis calculated) {
        validator.validate(calculated);
        var entity = new AnalysisResultEntity();
        entity.setCondition(conditions.getReferenceById(conditionId)); entity.setDataset(datasets.getReferenceById(datasetId));
        entity.setAsOfDate(calculated.asOfDate()); entity.setCurrentState(calculated.currentState());
        entity.setSampleCount(calculated.sampleCount()); entity.setTransitionCount(calculated.transitionCount());
        entity.setPredictionStatus(calculated.predictionStatus()); entity.setForecasts(json.write(calculated.forecasts()));
        entity.setQuality(json.write(Map.of("schemaVersion", 1, "stateOrder", calculated.stateOrder(), "warnings", calculated.warnings())));
        entity.setEngineVersion(calculated.engineVersion()); entity.setRuntime(json.write(calculated.runtime()));
        entity.setResultSha256(json.sha256(calculated)); entity.setCreatedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                var cell = new TransitionProbabilityEntity();
                cell.setId(new TransitionProbabilityId(null, STATES.get(row), STATES.get(column)));
                cell.setTransitionCount(calculated.transitionCounts().get(row).get(column));
                cell.setProbability(calculated.transitionMatrix().get(row).get(column)); entity.addTransition(cell);
            }
        }
        return domain(results.saveAndFlush(entity));
    }
    public Optional<AnalysisResult> findById(Long id) { return results.findById(id).map(this::domain); }
    public List<AnalysisResult> findAll() { return results.findAll().stream().map(this::domain).toList(); }
    private AnalysisResult domain(AnalysisResultEntity entity) {
        if (entity.getTransitions().size() != 9) { throw new CalculationInvariantFailedException("Stored analysis requires nine transitions"); }
        List<List<Integer>> counts = new ArrayList<>(); List<List<Double>> matrix = new ArrayList<>();
        for (String from : STATES) {
            List<Integer> countRow = new ArrayList<>(); List<Double> probabilityRow = new ArrayList<>();
            for (String to : STATES) {
                var cell = entity.getTransitions().stream().filter(value -> from.equals(value.getId().getFromState())
                        && to.equals(value.getId().getToState())).findFirst()
                        .orElseThrow(() -> new CalculationInvariantFailedException("Stored transition cell is missing"));
                countRow.add(cell.getTransitionCount()); probabilityRow.add(cell.getProbability());
            }
            counts.add(List.copyOf(countRow)); matrix.add(Collections.unmodifiableList(probabilityRow));
        }
        var quality = json.read(entity.getQuality(), Quality.class);
        var calculated = new CalculatedAnalysis(quality.stateOrder(), entity.getAsOfDate(), entity.getCurrentState(),
                entity.getSampleCount(), entity.getTransitionCount(), List.copyOf(counts), List.copyOf(matrix),
                entity.getPredictionStatus(), json.read(entity.getForecasts(), new TypeReference<List<ForecastPayload>>() {}),
                quality.warnings(), entity.getEngineVersion(), json.read(entity.getRuntime(), new TypeReference<Map<String, Object>>() {}));
        return new AnalysisResult(entity.getId(), entity.getCondition().getId(), entity.getDataset().getId(), calculated, entity.getCreatedAt());
    }
    private record Quality(Integer schemaVersion, List<String> stateOrder, List<AnalysisWarning> warnings) {}
}
