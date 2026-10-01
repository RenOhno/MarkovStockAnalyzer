package com.example.markovstockanalyzer.repository;

import com.example.markovstockanalyzer.dto.response.StockSummary;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class InMemoryStockRepository implements StockRepository {
    private final List<StockSummary> stocks = List.of(
            new StockSummary("9001", "TEST", "Synthetic Test Stock", "XTKS", "JPY", "Asia/Tokyo"),
            new StockSummary("7203", "7203.T", "Toyota Motor", "XTKS", "JPY", "Asia/Tokyo")
    );

    @Override
    public List<StockSummary> findAll() {
        return stocks;
    }

    @Override
    public boolean existsById(String id) {
        return stocks.stream().anyMatch(stock -> stock.id().equals(id));
    }
}
