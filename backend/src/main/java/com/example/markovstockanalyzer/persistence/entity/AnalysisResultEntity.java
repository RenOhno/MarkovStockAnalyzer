package com.example.markovstockanalyzer.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Entity
@Table(name = "analysis_results")
public class AnalysisResultEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "bigint unsigned")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "condition_id", nullable = false, foreignKey = @ForeignKey(name = "fk_analysis_condition"))
    private AnalysisConditionEntity condition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dataset_id", nullable = false, foreignKey = @ForeignKey(name = "fk_analysis_dataset"))
    private PriceDatasetEntity dataset;

    @Column(name = "as_of_date", nullable = false, columnDefinition = "date")
    private LocalDate asOfDate;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "current_state", nullable = false, columnDefinition = "char(4)", length = 4)
    private String currentState;

    @Column(name = "sample_count", nullable = false, columnDefinition = "int unsigned")
    private Integer sampleCount;

    @Column(name = "transition_count", nullable = false, columnDefinition = "int unsigned")
    private Integer transitionCount;

    @Column(name = "prediction_status", nullable = false, columnDefinition = "varchar(16)", length = 16)
    private String predictionStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "forecasts", nullable = false, columnDefinition = "json")
    private String forecasts;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quality", nullable = false, columnDefinition = "json")
    private String quality;

    @Column(name = "engine_version", nullable = false, columnDefinition = "varchar(64)", length = 64)
    private String engineVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "runtime", nullable = false, columnDefinition = "json")
    private String runtime;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    @OneToMany(mappedBy = "analysis", cascade = CascadeType.PERSIST)
    private List<TransitionProbabilityEntity> transitions = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "result_sha256", nullable = false, columnDefinition = "char(64)", length = 64)
    private String resultSha256;

    public String getResultSha256() { return resultSha256; }
    public void setResultSha256(String resultSha256) { this.resultSha256 = resultSha256; }

    public AnalysisResultEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public AnalysisConditionEntity getCondition() { return condition; }
    public void setCondition(AnalysisConditionEntity condition) { this.condition = condition; }
    public PriceDatasetEntity getDataset() { return dataset; }
    public void setDataset(PriceDatasetEntity dataset) { this.dataset = dataset; }
    public LocalDate getAsOfDate() { return asOfDate; }
    public void setAsOfDate(LocalDate asOfDate) { this.asOfDate = asOfDate; }
    public String getCurrentState() { return currentState; }
    public void setCurrentState(String currentState) { this.currentState = currentState; }
    public Integer getSampleCount() { return sampleCount; }
    public void setSampleCount(Integer sampleCount) { this.sampleCount = sampleCount; }
    public Integer getTransitionCount() { return transitionCount; }
    public void setTransitionCount(Integer transitionCount) { this.transitionCount = transitionCount; }
    public String getPredictionStatus() { return predictionStatus; }
    public void setPredictionStatus(String predictionStatus) { this.predictionStatus = predictionStatus; }
    public String getForecasts() { return forecasts; }
    public void setForecasts(String forecasts) { this.forecasts = forecasts; }
    public String getQuality() { return quality; }
    public void setQuality(String quality) { this.quality = quality; }
    public String getEngineVersion() { return engineVersion; }
    public void setEngineVersion(String engineVersion) { this.engineVersion = engineVersion; }
    public String getRuntime() { return runtime; }
    public void setRuntime(String runtime) { this.runtime = runtime; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public List<TransitionProbabilityEntity> getTransitions() { return Collections.unmodifiableList(transitions); }
    public void addTransition(TransitionProbabilityEntity child) {
        transitions.add(child);
        child.setAnalysis(this);
    }

    @PrePersist
    private void initializeCreatedAt() {
        if (createdAt == null) { createdAt = Instant.now(); }
    }
}
