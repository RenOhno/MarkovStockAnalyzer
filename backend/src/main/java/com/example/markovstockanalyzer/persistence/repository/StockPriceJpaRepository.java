package com.example.markovstockanalyzer.persistence.repository;

import com.example.markovstockanalyzer.persistence.entity.StockPriceEntity;
import com.example.markovstockanalyzer.persistence.entity.StockPriceId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockPriceJpaRepository extends JpaRepository<StockPriceEntity, StockPriceId> {
}
