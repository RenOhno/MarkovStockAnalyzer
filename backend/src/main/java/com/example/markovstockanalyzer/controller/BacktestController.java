package com.example.markovstockanalyzer.controller;

import com.example.markovstockanalyzer.dto.request.CreateBacktestRequest;
import com.example.markovstockanalyzer.dto.response.BacktestResultResponse;
import com.example.markovstockanalyzer.dto.response.BacktestPredictionsResponse;
import com.example.markovstockanalyzer.validation.Pagination;
import com.example.markovstockanalyzer.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/backtest")
public class BacktestController {
    private final BacktestService service;
    private final BacktestResultWriter writer;
    private final BacktestResultQueryService query;

    public BacktestController(BacktestService service, BacktestResultWriter writer, BacktestResultQueryService query) {
        this.service = service;
        this.writer = writer;
        this.query = query;
    }

    @PostMapping
    public ResponseEntity<BacktestResultResponse> create(@Valid @RequestBody CreateBacktestRequest request,
                                                        HttpServletRequest servletRequest) {
        String requestId = UUID.randomUUID().toString();
        servletRequest.setAttribute("requestId", requestId);
        var saved = writer.save(service.backtest(request, requestId));
        return ResponseEntity.created(URI.create("/api/backtest/" + saved.id())).header("X-Request-Id", requestId)
                .body(query.findById(saved.id()));
    }

    @GetMapping("/{id}")
    public BacktestResultResponse findById(@PathVariable("id") Long id) { return query.findById(id); }

    @GetMapping("/{id}/predictions")
    public BacktestPredictionsResponse predictions(@PathVariable("id") Long id,
            @RequestParam(defaultValue = "0") String page, @RequestParam(defaultValue = "20") String size) {
        return query.predictions(id, Pagination.parse(page), Pagination.parse(size));
    }
}
