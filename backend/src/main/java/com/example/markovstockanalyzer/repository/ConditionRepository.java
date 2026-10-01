package com.example.markovstockanalyzer.repository;

import com.example.markovstockanalyzer.dto.request.CreateConditionRequest;
import com.example.markovstockanalyzer.dto.response.ConditionResponse;

public interface ConditionRepository {
    ConditionResponse save(CreateConditionRequest request);
}
