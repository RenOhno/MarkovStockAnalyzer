package com.example.markovstockanalyzer.persistence.adapter;

import com.example.markovstockanalyzer.dto.request.CreateConditionRequest;
import com.example.markovstockanalyzer.dto.response.ConditionResponse;
import com.example.markovstockanalyzer.persistence.entity.AnalysisConditionEntity;
import com.example.markovstockanalyzer.persistence.repository.AnalysisConditionJpaRepository;
import com.example.markovstockanalyzer.persistence.repository.StockJpaRepository;
import com.example.markovstockanalyzer.repository.ConditionRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Repository
@Profile("mysql")
@Transactional(readOnly = true)
public class JpaConditionRepositoryAdapter implements ConditionRepository {
    private final AnalysisConditionJpaRepository conditions;
    private final StockJpaRepository stocks;
    private final PersistenceJson json;
    public JpaConditionRepositoryAdapter(AnalysisConditionJpaRepository conditions, StockJpaRepository stocks, PersistenceJson json) {
        this.conditions = conditions; this.stocks = stocks; this.json = json;
    }
    @Transactional
    public ConditionResponse save(CreateConditionRequest request) {
        var entity = new AnalysisConditionEntity();
        entity.setStock(stocks.getReferenceById(Long.valueOf(request.stockId())));
        entity.setName(request.name()); entity.setStartDate(request.startDate()); entity.setEndDate(request.endDate());
        entity.setLowerThreshold(request.lowerThreshold().setScale(10)); entity.setUpperThreshold(request.upperThreshold().setScale(10));
        entity.setStateCount(request.stateCount()); entity.setEstimator(request.estimator()); entity.setWindowMode(request.windowMode());
        entity.setWindowSize(request.windowSize()); entity.setHorizons(json.write(request.horizons()));
        entity.setCreatedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        return domain(conditions.saveAndFlush(entity));
    }
    public Optional<ConditionResponse> findById(Long id) { return conditions.findById(id).map(this::domain); }
    public List<ConditionResponse> findAll() { return conditions.findAll().stream().map(this::domain).toList(); }
    private ConditionResponse domain(AnalysisConditionEntity entity) {
        return new ConditionResponse(entity.getId(), entity.getName(), entity.getStock().getId().toString(),
                entity.getStartDate(), entity.getEndDate(), entity.getLowerThreshold(), entity.getUpperThreshold(),
                entity.getStateCount(), entity.getEstimator(), entity.getWindowMode(), entity.getWindowSize(),
                json.read(entity.getHorizons(), new TypeReference<List<Integer>>() {}), entity.getCreatedAt());
    }
}
