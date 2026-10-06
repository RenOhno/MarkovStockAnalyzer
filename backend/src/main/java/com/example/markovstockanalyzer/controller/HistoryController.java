package com.example.markovstockanalyzer.controller;

import com.example.markovstockanalyzer.dto.response.HistoryPageResponse;
import com.example.markovstockanalyzer.service.HistoryService;
import com.example.markovstockanalyzer.validation.Pagination;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/history")
public class HistoryController {
    private final HistoryService service;

    public HistoryController(HistoryService service) {
        this.service = service;
    }

    @GetMapping
    public HistoryPageResponse findAll(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "stockId", required = false) String stockId,
            @RequestParam(value = "page", defaultValue = "0") String page,
            @RequestParam(value = "size", defaultValue = "20") String size
    ) {
        return service.findAll(type == null ? "ALL" : type, stockId, Pagination.parse(page), Pagination.parse(size));
    }
}
