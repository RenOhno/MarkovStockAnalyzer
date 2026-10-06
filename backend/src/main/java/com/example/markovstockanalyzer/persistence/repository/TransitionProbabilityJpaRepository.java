package com.example.markovstockanalyzer.persistence.repository;

import com.example.markovstockanalyzer.persistence.entity.TransitionProbabilityEntity;
import com.example.markovstockanalyzer.persistence.entity.TransitionProbabilityId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransitionProbabilityJpaRepository extends JpaRepository<TransitionProbabilityEntity, TransitionProbabilityId> {
}
