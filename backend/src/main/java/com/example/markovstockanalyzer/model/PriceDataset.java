package com.example.markovstockanalyzer.model;

import com.example.markovstockanalyzer.dto.response.PriceDatasetPayload;

import java.time.Instant;

public record PriceDataset(Long id, String stockId, PriceDatasetPayload dataset, Instant createdAt) {
}
