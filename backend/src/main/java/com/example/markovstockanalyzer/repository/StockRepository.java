package com.example.markovstockanalyzer.repository;

import com.example.markovstockanalyzer.dto.response.StockSummary;

import java.util.List;

public interface StockRepository {
    List<StockSummary> findAll();

    boolean existsById(String id);
}
