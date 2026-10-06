package com.example.markovstockanalyzer.persistence.repository;

import com.example.markovstockanalyzer.persistence.entity.AnalysisResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisResultJpaRepository extends JpaRepository<AnalysisResultEntity, Long> {
}
