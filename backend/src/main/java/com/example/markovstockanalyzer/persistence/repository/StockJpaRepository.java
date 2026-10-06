package com.example.markovstockanalyzer.persistence.repository;

import com.example.markovstockanalyzer.persistence.entity.StockEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockJpaRepository extends JpaRepository<StockEntity, Long> {
}
