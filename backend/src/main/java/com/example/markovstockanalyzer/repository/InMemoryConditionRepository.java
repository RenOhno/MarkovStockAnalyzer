package com.example.markovstockanalyzer.repository;

import com.example.markovstockanalyzer.dto.request.CreateConditionRequest;
import com.example.markovstockanalyzer.dto.response.ConditionResponse;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.Optional;

@Repository
public class InMemoryConditionRepository implements ConditionRepository {
    private final AtomicLong sequence = new AtomicLong(100);
    private final ConcurrentHashMap<Long, ConditionResponse> conditions = new ConcurrentHashMap<>();

    @Override
    public ConditionResponse save(CreateConditionRequest request) {
        long id = sequence.incrementAndGet();
        ConditionResponse response = new ConditionResponse(
                id,
                request.name(),
                request.stockId(),
                request.startDate(),
                request.endDate(),
                request.lowerThreshold(),
                request.upperThreshold(),
                request.stateCount(),
                request.estimator(),
                request.windowMode(),
                request.windowSize(),
                ListCopy.copy(request.horizons()),
                Instant.now()
        );
        conditions.put(id, response);
        return response;
    }

    @Override
    public List<ConditionResponse> findAll() {
        return List.copyOf(conditions.values());
    }

    @Override
    public Optional<ConditionResponse> findById(Long id) {
        return Optional.ofNullable(conditions.get(id));
    }

    private static final class ListCopy {
        private static java.util.List<Integer> copy(java.util.List<Integer> values) {
            return java.util.List.copyOf(values);
        }
    }
}
