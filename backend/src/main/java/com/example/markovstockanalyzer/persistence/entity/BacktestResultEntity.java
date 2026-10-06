package com.example.markovstockanalyzer.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Entity
@Table(name = "backtest_results")
public class BacktestResultEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "bigint unsigned")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "condition_id", nullable = false, foreignKey = @ForeignKey(name = "fk_backtest_condition"))
    private AnalysisConditionEntity condition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dataset_id", nullable = false, foreignKey = @ForeignKey(name = "fk_backtest_dataset"))
    private PriceDatasetEntity dataset;

    @Column(name = "test_start", nullable = false, columnDefinition = "date")
    private LocalDate testStart;

    @Column(name = "test_end", nullable = false, columnDefinition = "date")
    private LocalDate testEnd;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "horizon", nullable = false, columnDefinition = "smallint unsigned")
    private Integer horizon = 1;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "min_train_states", nullable = false, columnDefinition = "smallint unsigned")
    private Integer minTrainStates;

    @Column(name = "training_mode", nullable = false, columnDefinition = "varchar(16)", length = 16)
    private String trainingMode;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "window_size", nullable = true, columnDefinition = "smallint unsigned")
    private Integer windowSize;

    @Column(name = "eligible_count", nullable = false, columnDefinition = "int unsigned")
    private Integer eligibleCount;

    @Column(name = "predicted_count", nullable = false, columnDefinition = "int unsigned")
    private Integer predictedCount;

    @Column(name = "correct_count", nullable = false, columnDefinition = "int unsigned")
    private Integer correctCount;

    @Column(name = "skipped_count", nullable = false, columnDefinition = "int unsigned")
    private Integer skippedCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics", nullable = false, columnDefinition = "json")
    private String metrics;

    @Column(name = "engine_version", nullable = false, columnDefinition = "varchar(64)", length = 64)
    private String engineVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "runtime", nullable = false, columnDefinition = "json")
    private String runtime;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    @OneToMany(mappedBy = "backtest", cascade = CascadeType.PERSIST)
    private List<BacktestPredictionEntity> predictions = new ArrayList<>();

    public BacktestResultEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public AnalysisConditionEntity getCondition() { return condition; }
    public void setCondition(AnalysisConditionEntity condition) { this.condition = condition; }
    public PriceDatasetEntity getDataset() { return dataset; }
    public void setDataset(PriceDatasetEntity dataset) { this.dataset = dataset; }
    public LocalDate getTestStart() { return testStart; }
    public void setTestStart(LocalDate testStart) { this.testStart = testStart; }
    public LocalDate getTestEnd() { return testEnd; }
    public void setTestEnd(LocalDate testEnd) { this.testEnd = testEnd; }
    public Integer getHorizon() { return horizon; }
    public void setHorizon(Integer horizon) { this.horizon = horizon; }
    public Integer getMinTrainStates() { return minTrainStates; }
    public void setMinTrainStates(Integer minTrainStates) { this.minTrainStates = minTrainStates; }
    public String getTrainingMode() { return trainingMode; }
    public void setTrainingMode(String trainingMode) { this.trainingMode = trainingMode; }
    public Integer getWindowSize() { return windowSize; }
    public void setWindowSize(Integer windowSize) { this.windowSize = windowSize; }
    public Integer getEligibleCount() { return eligibleCount; }
    public void setEligibleCount(Integer eligibleCount) { this.eligibleCount = eligibleCount; }
    public Integer getPredictedCount() { return predictedCount; }
    public void setPredictedCount(Integer predictedCount) { this.predictedCount = predictedCount; }
    public Integer getCorrectCount() { return correctCount; }
    public void setCorrectCount(Integer correctCount) { this.correctCount = correctCount; }
    public Integer getSkippedCount() { return skippedCount; }
    public void setSkippedCount(Integer skippedCount) { this.skippedCount = skippedCount; }
    public String getMetrics() { return metrics; }
    public void setMetrics(String metrics) { this.metrics = metrics; }
    public String getEngineVersion() { return engineVersion; }
    public void setEngineVersion(String engineVersion) { this.engineVersion = engineVersion; }
    public String getRuntime() { return runtime; }
    public void setRuntime(String runtime) { this.runtime = runtime; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public List<BacktestPredictionEntity> getPredictions() { return Collections.unmodifiableList(predictions); }
    public void addPrediction(BacktestPredictionEntity child) {
        predictions.add(child);
        child.setBacktest(this);
    }

    @PrePersist
    private void initializeCreatedAt() {
        if (createdAt == null) { createdAt = Instant.now(); }
    }
}
