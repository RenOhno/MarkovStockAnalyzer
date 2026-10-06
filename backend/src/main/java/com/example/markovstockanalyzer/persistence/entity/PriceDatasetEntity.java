package com.example.markovstockanalyzer.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Entity
@Table(name = "price_datasets")
public class PriceDatasetEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, columnDefinition = "bigint unsigned")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false, foreignKey = @ForeignKey(name = "fk_dataset_stock"))
    private StockEntity stock;

    @Column(name = "provider", nullable = false, columnDefinition = "varchar(32)", length = 32)
    private String provider;

    @Column(name = "provider_version", nullable = false, columnDefinition = "varchar(64)", length = 64)
    private String providerVersion;

    @Column(name = "price_basis", nullable = false, columnDefinition = "varchar(32)", length = 32)
    private String priceBasis;

    @Column(name = "adjustment_policy", nullable = false, columnDefinition = "varchar(64)", length = 64)
    private String adjustmentPolicy;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "fetched_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant fetchedAt;

    @Column(name = "coverage_start", nullable = false, columnDefinition = "date")
    private LocalDate coverageStart;

    @Column(name = "coverage_end", nullable = false, columnDefinition = "date")
    private LocalDate coverageEnd;

    @Column(name = "row_count", nullable = false, columnDefinition = "int unsigned")
    private Integer rowCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "json")
    private String metadata;

    @OneToMany(mappedBy = "dataset", cascade = CascadeType.PERSIST)
    private List<StockPriceEntity> prices = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "content_sha256", nullable = false, columnDefinition = "char(64)", length = 64)
    private String contentSha256;

    public String getContentSha256() { return contentSha256; }
    public void setContentSha256(String contentSha256) { this.contentSha256 = contentSha256; }

    public PriceDatasetEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public StockEntity getStock() { return stock; }
    public void setStock(StockEntity stock) { this.stock = stock; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getProviderVersion() { return providerVersion; }
    public void setProviderVersion(String providerVersion) { this.providerVersion = providerVersion; }
    public String getPriceBasis() { return priceBasis; }
    public void setPriceBasis(String priceBasis) { this.priceBasis = priceBasis; }
    public String getAdjustmentPolicy() { return adjustmentPolicy; }
    public void setAdjustmentPolicy(String adjustmentPolicy) { this.adjustmentPolicy = adjustmentPolicy; }
    public Instant getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Instant fetchedAt) { this.fetchedAt = fetchedAt; }
    public LocalDate getCoverageStart() { return coverageStart; }
    public void setCoverageStart(LocalDate coverageStart) { this.coverageStart = coverageStart; }
    public LocalDate getCoverageEnd() { return coverageEnd; }
    public void setCoverageEnd(LocalDate coverageEnd) { this.coverageEnd = coverageEnd; }
    public Integer getRowCount() { return rowCount; }
    public void setRowCount(Integer rowCount) { this.rowCount = rowCount; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public List<StockPriceEntity> getPrices() { return Collections.unmodifiableList(prices); }
    public void addPrice(StockPriceEntity child) {
        prices.add(child);
        child.setDataset(this);
    }
}
