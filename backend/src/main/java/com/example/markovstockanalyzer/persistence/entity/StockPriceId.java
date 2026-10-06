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
public class StockPriceId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "dataset_id", nullable = false, columnDefinition = "bigint unsigned")
    private Long datasetId;

    @Column(name = "trade_date", nullable = false, columnDefinition = "date")
    private LocalDate tradeDate;

    public StockPriceId() {}
    public StockPriceId(Long datasetId, LocalDate tradeDate) {
        this.datasetId = datasetId;
        this.tradeDate = tradeDate;
    }

    public Long getDatasetId() { return datasetId; }
    public void setDatasetId(Long datasetId) { this.datasetId = datasetId; }
    public LocalDate getTradeDate() { return tradeDate; }
    public void setTradeDate(LocalDate tradeDate) { this.tradeDate = tradeDate; }

    @Override public boolean equals(Object other) {
        if (this == other) { return true; }
        if (!(other instanceof StockPriceId that)) { return false; }
        return Objects.equals(datasetId, that.datasetId) && Objects.equals(tradeDate, that.tradeDate);
    }
    @Override public int hashCode() { return Objects.hash(datasetId, tradeDate); }
}
