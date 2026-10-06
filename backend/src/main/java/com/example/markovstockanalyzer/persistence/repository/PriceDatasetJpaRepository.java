package com.example.markovstockanalyzer.persistence.repository;

import com.example.markovstockanalyzer.persistence.entity.PriceDatasetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceDatasetJpaRepository extends JpaRepository<PriceDatasetEntity, Long> {
}
