package com.example.markovstockanalyzer.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Entity
@Table(name = "analysis_conditions")
public class AnalysisConditionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "bigint unsigned")
    private Long id;

    @Column(name = "name", nullable = false, columnDefinition = "varchar(100)", length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false, foreignKey = @ForeignKey(name = "fk_condition_stock"))
    private StockEntity stock;

    @Column(name = "start_date", nullable = false, columnDefinition = "date")
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false, columnDefinition = "date")
    private LocalDate endDate;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "state_count", nullable = false, columnDefinition = "tinyint unsigned")
    private Integer stateCount = 3;

    @Column(name = "lower_threshold", nullable = false, columnDefinition = "decimal(12,10)", precision = 12, scale = 10)
    private BigDecimal lowerThreshold;

    @Column(name = "upper_threshold", nullable = false, columnDefinition = "decimal(12,10)", precision = 12, scale = 10)
    private BigDecimal upperThreshold;

    @Column(name = "estimator", nullable = false, columnDefinition = "varchar(32)", length = 32)
    private String estimator;

    @Column(name = "window_mode", nullable = false, columnDefinition = "varchar(16)", length = 16)
    private String windowMode;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "window_size", nullable = true, columnDefinition = "smallint unsigned")
    private Integer windowSize;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "horizons", nullable = false, columnDefinition = "json")
    private String horizons;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "schema_version", nullable = false, columnDefinition = "smallint unsigned")
    private Integer schemaVersion = 1;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    public AnalysisConditionEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public StockEntity getStock() { return stock; }
    public void setStock(StockEntity stock) { this.stock = stock; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public Integer getStateCount() { return stateCount; }
    public void setStateCount(Integer stateCount) { this.stateCount = stateCount; }
    public BigDecimal getLowerThreshold() { return lowerThreshold; }
    public void setLowerThreshold(BigDecimal lowerThreshold) { this.lowerThreshold = lowerThreshold; }
    public BigDecimal getUpperThreshold() { return upperThreshold; }
    public void setUpperThreshold(BigDecimal upperThreshold) { this.upperThreshold = upperThreshold; }
    public String getEstimator() { return estimator; }
    public void setEstimator(String estimator) { this.estimator = estimator; }
    public String getWindowMode() { return windowMode; }
    public void setWindowMode(String windowMode) { this.windowMode = windowMode; }
    public Integer getWindowSize() { return windowSize; }
    public void setWindowSize(Integer windowSize) { this.windowSize = windowSize; }
    public String getHorizons() { return horizons; }
    public void setHorizons(String horizons) { this.horizons = horizons; }
    public Integer getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(Integer schemaVersion) { this.schemaVersion = schemaVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @PrePersist
    private void initializeCreatedAt() {
        if (createdAt == null) { createdAt = Instant.now(); }
    }
}
