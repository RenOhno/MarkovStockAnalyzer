package com.example.markovstockanalyzer.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.io.Serializable;

@Embeddable
public class BacktestPredictionId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "backtest_id", nullable = false, columnDefinition = "bigint unsigned")
    private Long backtestId;

    @Column(name = "target_date", nullable = false, columnDefinition = "date")
    private LocalDate targetDate;

    public BacktestPredictionId() {}
    public BacktestPredictionId(Long backtestId, LocalDate targetDate) {
        this.backtestId = backtestId;
        this.targetDate = targetDate;
    }

    public Long getBacktestId() { return backtestId; }
    public void setBacktestId(Long backtestId) { this.backtestId = backtestId; }
    public LocalDate getTargetDate() { return targetDate; }
    public void setTargetDate(LocalDate targetDate) { this.targetDate = targetDate; }

    @Override public boolean equals(Object other) {
        if (this == other) { return true; }
        if (!(other instanceof BacktestPredictionId that)) { return false; }
        return Objects.equals(backtestId, that.backtestId) && Objects.equals(targetDate, that.targetDate);
    }
    @Override public int hashCode() { return Objects.hash(backtestId, targetDate); }
}
