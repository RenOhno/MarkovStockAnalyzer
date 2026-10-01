package com.example.markovstockanalyzer.controller;

import com.example.markovstockanalyzer.dto.request.CreateConditionRequest;
import com.example.markovstockanalyzer.dto.response.ConditionResponse;
import com.example.markovstockanalyzer.service.ConditionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/conditions")
public class ConditionController {
    private final ConditionService service;

    public ConditionController(ConditionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ConditionResponse> create(
            @Valid @RequestBody CreateConditionRequest request,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId
    ) {
        ConditionResponse response = service.create(request);
        return ResponseEntity
                .created(URI.create("/api/conditions/" + response.id()))
                .body(response);
    }

    @GetMapping
    public List<ConditionResponse> findAll(
            @RequestParam(value = "stockId", required = false) String stockId
    ) {
        return service.findAll(stockId);
    }

    @GetMapping("/{id}")
    public ConditionResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }
}
