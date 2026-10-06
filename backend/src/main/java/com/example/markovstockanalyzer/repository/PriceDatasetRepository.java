package com.example.markovstockanalyzer.repository;

import com.example.markovstockanalyzer.dto.response.PriceDatasetPayload;
import com.example.markovstockanalyzer.model.PriceDataset;

import java.util.Optional;

public interface PriceDatasetRepository {
    PriceDataset save(String stockId, PriceDatasetPayload dataset);

    Optional<PriceDataset> findById(Long id);
}
