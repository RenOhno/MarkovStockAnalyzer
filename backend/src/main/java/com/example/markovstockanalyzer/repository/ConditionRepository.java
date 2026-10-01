package com.example.markovstockanalyzer.repository;

import com.example.markovstockanalyzer.dto.request.CreateConditionRequest;
import com.example.markovstockanalyzer.dto.response.ConditionResponse;

import java.util.List;
import java.util.Optional;

public interface ConditionRepository {
    ConditionResponse save(CreateConditionRequest request);

    List<ConditionResponse> findAll();

    Optional<ConditionResponse> findById(Long id);
}
