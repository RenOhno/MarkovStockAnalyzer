package com.example.markovstockanalyzer.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Entity
@Table(name = "stock_prices")
public class StockPriceEntity {
    @EmbeddedId
    private StockPriceId id = new StockPriceId();

    @Column(name = "close", nullable = false, columnDefinition = "decimal(24,10)", precision = 24, scale = 10)
    private BigDecimal close;

    @Column(name = "adjusted_close", nullable = false, columnDefinition = "decimal(24,10)", precision = 24, scale = 10)
    private BigDecimal adjustedClose;

    @Column(name = "volume", nullable = true, columnDefinition = "bigint unsigned")
    private Long volume;

    @MapsId("datasetId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dataset_id", nullable = false, foreignKey = @ForeignKey(name = "fk_price_dataset"))
    private PriceDatasetEntity dataset;

    public PriceDatasetEntity getDataset() { return dataset; }
    public void setDataset(PriceDatasetEntity dataset) { this.dataset = dataset; }

    public StockPriceEntity() {}

    public StockPriceId getId() { return id; }
    public void setId(StockPriceId id) { this.id = id; }
    public BigDecimal getClose() { return close; }
    public void setClose(BigDecimal close) { this.close = close; }
    public BigDecimal getAdjustedClose() { return adjustedClose; }
    public void setAdjustedClose(BigDecimal adjustedClose) { this.adjustedClose = adjustedClose; }
    public Long getVolume() { return volume; }
    public void setVolume(Long volume) { this.volume = volume; }
}
