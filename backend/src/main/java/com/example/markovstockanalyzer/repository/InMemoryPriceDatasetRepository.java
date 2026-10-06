package com.example.markovstockanalyzer.repository;

import com.example.markovstockanalyzer.dto.response.PriceDatasetPayload;
import com.example.markovstockanalyzer.model.PriceDataset;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryPriceDatasetRepository implements PriceDatasetRepository {
    private final AtomicLong sequence = new AtomicLong();
    private final ConcurrentHashMap<Long, PriceDataset> datasets = new ConcurrentHashMap<>();

    @Override
    public PriceDataset save(String stockId, PriceDatasetPayload dataset) {
        Objects.requireNonNull(stockId, "stockId");
        PriceDatasetPayload snapshot = InMemorySnapshots.dataset(dataset);
        long id = sequence.incrementAndGet();
        PriceDataset saved = new PriceDataset(id, stockId, snapshot, Instant.now());
        datasets.put(id, saved);
        return saved;
    }

    @Override
    public Optional<PriceDataset> findById(Long id) {
        return Optional.ofNullable(datasets.get(id));
    }
}
