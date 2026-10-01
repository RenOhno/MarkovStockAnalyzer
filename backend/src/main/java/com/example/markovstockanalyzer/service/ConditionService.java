package com.example.markovstockanalyzer.service;

import com.example.markovstockanalyzer.dto.request.CreateConditionRequest;
import com.example.markovstockanalyzer.dto.response.ConditionResponse;
import com.example.markovstockanalyzer.exception.InvalidConditionException;
import com.example.markovstockanalyzer.repository.ConditionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class ConditionService {
    private static final int STATE_COUNT = 3;
    private static final String ESTIMATOR = "MLE_STRICT";
    private static final String WINDOW_MODE = "FULL";
    private static final List<Integer> HORIZONS = List.of(1, 3, 5, 10);

    private final ConditionRepository repository;

    public ConditionService(ConditionRepository repository) {
        this.repository = repository;
    }

    public ConditionResponse create(CreateConditionRequest request) {
        if (request.startDate().isAfter(request.endDate())) {
            throw new InvalidConditionException("startDate must be on or before endDate");
        }
        if (request.endDate().isAfter(maxEndDate(request.startDate()))) {
            throw new InvalidConditionException("condition period exceeds five calendar years");
        }
        if (request.lowerThreshold().compareTo(request.upperThreshold()) >= 0
                || request.lowerThreshold().compareTo(java.math.BigDecimal.valueOf(-1)) <= 0
                || request.upperThreshold().compareTo(java.math.BigDecimal.ONE) >= 0) {
            throw new InvalidConditionException("thresholds are outside the supported range");
        }
        if (request.stateCount() != STATE_COUNT) {
            throw new InvalidConditionException("stateCount must be 3");
        }
        if (!ESTIMATOR.equals(request.estimator())) {
            throw new InvalidConditionException("estimator must be MLE_STRICT");
        }
        if (!WINDOW_MODE.equals(request.windowMode())) {
            throw new InvalidConditionException("windowMode must be FULL");
        }
        if (request.windowSize() != null) {
            throw new InvalidConditionException("windowSize must be null");
        }
        if (!HORIZONS.equals(request.horizons())) {
            throw new InvalidConditionException("horizons must be [1, 3, 5, 10]");
        }
        return repository.save(request);
    }

    private LocalDate maxEndDate(LocalDate startDate) {
        try {
            return startDate.plusYears(5);
        } catch (java.time.DateTimeException exception) {
            return LocalDate.of(startDate.getYear() + 5, 2, 28);
        }
    }
}
