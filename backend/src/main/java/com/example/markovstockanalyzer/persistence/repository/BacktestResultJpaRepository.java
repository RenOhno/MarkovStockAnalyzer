package com.example.markovstockanalyzer.persistence.repository;

import com.example.markovstockanalyzer.persistence.entity.BacktestResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BacktestResultJpaRepository extends JpaRepository<BacktestResultEntity, Long> {
}
