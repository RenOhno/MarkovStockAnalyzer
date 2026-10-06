package com.example.markovstockanalyzer.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Entity
@Table(name = "backtest_predictions")
public class BacktestPredictionEntity {
    @EmbeddedId
    private BacktestPredictionId id = new BacktestPredictionId();

    @Column(name = "origin_date", nullable = false, columnDefinition = "date")
    private LocalDate originDate;

    @Column(name = "train_start", nullable = false, columnDefinition = "date")
    private LocalDate trainStart;

    @Column(name = "train_end", nullable = false, columnDefinition = "date")
    private LocalDate trainEnd;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "actual_state", nullable = false, columnDefinition = "char(4)", length = 4)
    private String actualState;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "predicted_state", nullable = true, columnDefinition = "char(4)", length = 4)
    private String predictedState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "probabilities", nullable = true, columnDefinition = "json")
    private String probabilities;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "majority_state", nullable = true, columnDefinition = "char(4)", length = 4)
    private String majorityState;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "persistence_state", nullable = true, columnDefinition = "char(4)", length = 4)
    private String persistenceState;

    @Column(name = "status", nullable = false, columnDefinition = "varchar(16)", length = 16)
    private String status;

    @Column(name = "skip_code", nullable = true, columnDefinition = "varchar(64)", length = 64)
    private String skipCode;

    @MapsId("backtestId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "backtest_id", nullable = false, foreignKey = @ForeignKey(name = "fk_prediction_backtest"))
    private BacktestResultEntity backtest;

    public BacktestResultEntity getBacktest() { return backtest; }
    public void setBacktest(BacktestResultEntity backtest) { this.backtest = backtest; }

    public BacktestPredictionEntity() {}

    public BacktestPredictionId getId() { return id; }
    public void setId(BacktestPredictionId id) { this.id = id; }
    public LocalDate getOriginDate() { return originDate; }
    public void setOriginDate(LocalDate originDate) { this.originDate = originDate; }
    public LocalDate getTrainStart() { return trainStart; }
    public void setTrainStart(LocalDate trainStart) { this.trainStart = trainStart; }
    public LocalDate getTrainEnd() { return trainEnd; }
    public void setTrainEnd(LocalDate trainEnd) { this.trainEnd = trainEnd; }
    public String getActualState() { return actualState; }
    public void setActualState(String actualState) { this.actualState = actualState; }
    public String getPredictedState() { return predictedState; }
    public void setPredictedState(String predictedState) { this.predictedState = predictedState; }
    public String getProbabilities() { return probabilities; }
    public void setProbabilities(String probabilities) { this.probabilities = probabilities; }
    public String getMajorityState() { return majorityState; }
    public void setMajorityState(String majorityState) { this.majorityState = majorityState; }
    public String getPersistenceState() { return persistenceState; }
    public void setPersistenceState(String persistenceState) { this.persistenceState = persistenceState; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSkipCode() { return skipCode; }
    public void setSkipCode(String skipCode) { this.skipCode = skipCode; }
}
