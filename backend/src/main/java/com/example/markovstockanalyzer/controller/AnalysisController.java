package com.example.markovstockanalyzer.controller;

import com.example.markovstockanalyzer.dto.request.CreateAnalysisRequest;
import com.example.markovstockanalyzer.dto.response.AnalysisResultResponse;
import com.example.markovstockanalyzer.exception.PriceDatasetNotFoundException;
import com.example.markovstockanalyzer.mapper.AnalysisResultResponseMapper;
import com.example.markovstockanalyzer.model.AnalysisResult;
import com.example.markovstockanalyzer.model.PriceDataset;
import com.example.markovstockanalyzer.repository.PriceDatasetRepository;
import com.example.markovstockanalyzer.service.AnalysisExecutionResult;
import com.example.markovstockanalyzer.service.AnalysisResultWriter;
import com.example.markovstockanalyzer.service.AnalysisService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {
    private final AnalysisService service;
    private final AnalysisResultWriter writer;
    private final AnalysisResultResponseMapper mapper;
    private final PriceDatasetRepository datasets;

    public AnalysisController(AnalysisService service, AnalysisResultWriter writer,
                              AnalysisResultResponseMapper mapper, PriceDatasetRepository datasets) {
        this.service = service;
        this.writer = writer;
        this.mapper = mapper;
        this.datasets = datasets;
    }

    @PostMapping
    public ResponseEntity<AnalysisResultResponse> create(
            @Valid @RequestBody CreateAnalysisRequest request, HttpServletRequest servletRequest
    ) {
        String requestId = UUID.randomUUID().toString();
        servletRequest.setAttribute("requestId", requestId);
        AnalysisExecutionResult execution = service.analyze(request.conditionId(), request.datasetId(), requestId);
        AnalysisResult saved = writer.save(execution, request.datasetId());
        PriceDataset dataset = datasets.findById(saved.datasetId())
                .orElseThrow(() -> new PriceDatasetNotFoundException(saved.datasetId()));
        AnalysisResultResponse response = mapper.map(saved, dataset);
        return ResponseEntity.created(URI.create("/api/analysis/" + response.id()))
                .header("X-Request-Id", requestId)
                .body(response);
    }
}
