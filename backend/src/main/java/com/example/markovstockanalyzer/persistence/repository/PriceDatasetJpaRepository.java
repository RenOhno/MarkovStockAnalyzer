package com.example.markovstockanalyzer.persistence.repository;

import com.example.markovstockanalyzer.persistence.entity.PriceDatasetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceDatasetJpaRepository extends JpaRepository<PriceDatasetEntity, Long> {
    @org.springframework.data.jpa.repository.Query("select d from PriceDatasetEntity d where d.stock.id = :stockId"
            + " and d.provider = :provider and d.priceBasis = :priceBasis and d.adjustmentPolicy = :adjustmentPolicy"
            + " and d.fetchedAt >= :since and d.fetchedAt <= :now"
            + " and (exists (select a.id from AnalysisResultEntity a where a.dataset = d"
            + " and a.condition.startDate <= :startDate and a.condition.endDate >= :endDate)"
            + " or exists (select b.id from BacktestResultEntity b where b.dataset = d"
            + " and b.condition.startDate <= :startDate and b.condition.endDate >= :endDate))"
            + " order by d.fetchedAt desc, d.id desc")
    java.util.List<PriceDatasetEntity> cacheCandidates(Long stockId, String provider, String priceBasis,
            String adjustmentPolicy, java.time.Instant since, java.time.Instant now,
            java.time.LocalDate startDate, java.time.LocalDate endDate);
}
