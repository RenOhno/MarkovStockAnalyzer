package com.example.markovstockanalyzer.controller;

import com.example.markovstockanalyzer.dto.response.StockSummary;
import com.example.markovstockanalyzer.repository.StockRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stocks")
public class StockController {
    private final StockRepository repository;

    public StockController(StockRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<StockSummary> findAll() {
        return repository.findAll();
    }
}
