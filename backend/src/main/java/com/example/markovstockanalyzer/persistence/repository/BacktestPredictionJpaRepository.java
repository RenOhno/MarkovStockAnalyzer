package com.example.markovstockanalyzer.persistence.repository;

import com.example.markovstockanalyzer.persistence.entity.BacktestPredictionEntity;
import com.example.markovstockanalyzer.persistence.entity.BacktestPredictionId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BacktestPredictionJpaRepository extends JpaRepository<BacktestPredictionEntity, BacktestPredictionId> {
}
