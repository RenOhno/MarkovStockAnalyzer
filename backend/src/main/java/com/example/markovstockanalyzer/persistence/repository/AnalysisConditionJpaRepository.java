package com.example.markovstockanalyzer.persistence.repository;

import com.example.markovstockanalyzer.persistence.entity.AnalysisConditionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisConditionJpaRepository extends JpaRepository<AnalysisConditionEntity, Long> {
}
