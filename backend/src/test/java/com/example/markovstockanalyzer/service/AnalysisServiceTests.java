package com.example.markovstockanalyzer.service;

import com.example.markovstockanalyzer.client.PythonAnalysisClient;
import com.example.markovstockanalyzer.dto.request.AnalysisCondition;
import com.example.markovstockanalyzer.dto.request.AnalyzeInput;
import com.example.markovstockanalyzer.dto.request.FetchPricesRequest;
import com.example.markovstockanalyzer.dto.response.CalculatedAnalysis;
import com.example.markovstockanalyzer.dto.response.ConditionResponse;
import com.example.markovstockanalyzer.dto.response.ForecastPayload;
import com.example.markovstockanalyzer.dto.response.PriceDatasetPayload;
import com.example.markovstockanalyzer.dto.response.PricePoint;
import com.example.markovstockanalyzer.dto.response.StockSummary;
import com.example.markovstockanalyzer.exception.ApiErrorResponse;
import com.example.markovstockanalyzer.exception.CalculationInvariantFailedException;
import com.example.markovstockanalyzer.exception.ConditionNotFoundException;
import com.example.markovstockanalyzer.exception.PythonApiException;
import com.example.markovstockanalyzer.exception.StockNotFoundException;
import com.example.markovstockanalyzer.model.AnalysisResult;
import com.example.markovstockanalyzer.model.PriceDataset;
import com.example.markovstockanalyzer.repository.InMemoryAnalysisResultRepository;
import com.example.markovstockanalyzer.repository.InMemoryPriceDatasetRepository;
import com.example.markovstockanalyzer.repository.StockRepository;
import com.example.markovstockanalyzer.validation.AnalysisResultValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceTests {
    private static final Long CONDITION_ID = 101L;
    private static final String REQUEST_ID = "request-service-001";
    private static final ConditionResponse CONDITION = new ConditionResponse(
            CONDITION_ID, "Saved condition", "7203",
            LocalDate.parse("2025-01-06"), LocalDate.parse("2025-02-19"),
            new BigDecimal("-0.005"), new BigDecimal("0.007"), 3,
            "MLE_STRICT", "FULL", null, List.of(1, 3, 5, 10), Instant.parse("2025-01-01T00:00:00Z")
    );
    private static final StockSummary STOCK = new StockSummary(
            "7203", "7203.T", "Toyota Motor", "XTKS", "JPY", "Asia/Tokyo"
    );
    private static final PriceDatasetPayload DATASET = new PriceDatasetPayload(
            "7203.T", "XTKS", "Asia/Tokyo", "PROVIDER_ADJUSTED_CLOSE", "YFINANCE",
            "0.2.65", "PROVIDER_ADJUSTED_CLOSE_V1", OffsetDateTime.parse("2026-10-06T01:02:03Z"),
            LocalDate.parse("2024-12-30"), LocalDate.parse("2025-02-19"), "a".repeat(64),
            Map.of("schemaVersion", 1, "normalizationVersion", "NORMALIZATION_V1"),
            List.of(new PricePoint(LocalDate.parse("2024-12-30"), "100.0000000000", "100.0000000000", null))
    );
    private static final CalculatedAnalysis CALCULATED = new CalculatedAnalysis(
            List.of("UP", "FLAT", "DOWN"), LocalDate.parse("2025-02-19"), "FLAT", 31, 30,
            List.of(List.of(7, 4, 4), List.of(3, 0, 4), List.of(4, 4, 0)),
            List.of(List.of(7.0 / 15, 4.0 / 15, 4.0 / 15), List.of(3.0 / 7, 0.0, 4.0 / 7), List.of(0.5, 0.5, 0.0)),
            "AVAILABLE", List.of(
                    new ForecastPayload(1, List.of(0.4, 0.2, 0.4)), new ForecastPayload(3, List.of(0.4, 0.2, 0.4)),
                    new ForecastPayload(5, List.of(0.4, 0.2, 0.4)), new ForecastPayload(10, List.of(0.4, 0.2, 0.4))
            ), List.of(), "msa-core-v1", Map.of()
    );

    @Mock
    private ConditionService conditionService;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private PythonAnalysisClient pythonClient;
    @Mock
    private AnalysisResultValidator validator;
    private AnalysisService service;

    @BeforeEach
    void setUp() {
        service = new AnalysisService(conditionService, stockRepository, pythonClient, validator);
    }

    @Test
    void fetchesThenAnalyzesThenValidatesUsingSavedValuesAndOneRequestId() {
        givenSavedConditionAndStock();
        when(pythonClient.fetchPrices(any(FetchPricesRequest.class))).thenReturn(DATASET);
        when(pythonClient.analyze(any(AnalyzeInput.class))).thenReturn(CALCULATED);

        AnalysisExecutionResult result = service.analyze(CONDITION_ID, REQUEST_ID);

        ArgumentCaptor<FetchPricesRequest> fetch = ArgumentCaptor.forClass(FetchPricesRequest.class);
        ArgumentCaptor<AnalyzeInput> analyze = ArgumentCaptor.forClass(AnalyzeInput.class);
        InOrder order = inOrder(conditionService, stockRepository, pythonClient, validator);
        order.verify(conditionService).findById(CONDITION_ID);
        order.verify(stockRepository).findAll();
        order.verify(pythonClient).fetchPrices(fetch.capture());
        order.verify(pythonClient).analyze(analyze.capture());
        order.verify(validator).validate(CALCULATED, "msa-core-v1");
        verifyNoMoreInteractions(conditionService, stockRepository, pythonClient, validator);

        assertEquals(new FetchPricesRequest(
                REQUEST_ID, "7203.T", "XTKS", "Asia/Tokyo", CONDITION.startDate(), CONDITION.endDate(),
                true, "PROVIDER_ADJUSTED_CLOSE", "YFINANCE"
        ), fetch.getValue());
        assertEquals(new AnalysisCondition(
                CONDITION.startDate(), CONDITION.endDate(), new BigDecimal("-0.005"), new BigDecimal("0.007"),
                3, "MLE_STRICT", "FULL", null, List.of(1, 3, 5, 10)
        ), analyze.getValue().condition());
        assertEquals("msa-core-v1", analyze.getValue().engineVersion());
        assertEquals(REQUEST_ID, analyze.getValue().requestId());
        assertEquals(fetch.getValue().requestId(), analyze.getValue().requestId());
        assertSame(DATASET, analyze.getValue().dataset());
        assertSame(CONDITION, result.condition());
        assertSame(DATASET, result.dataset());
        assertSame(CALCULATED, result.calculatedAnalysis());
    }

    @Test
    void executionResultProvidesConditionDatasetAndAnalysisForCallerToSave() {
        givenSavedConditionAndStock();
        when(pythonClient.fetchPrices(any(FetchPricesRequest.class))).thenReturn(DATASET);
        when(pythonClient.analyze(any(AnalyzeInput.class))).thenReturn(CALCULATED);

        AnalysisExecutionResult execution = service.analyze(CONDITION_ID, REQUEST_ID);
        InMemoryPriceDatasetRepository datasets = new InMemoryPriceDatasetRepository();
        InMemoryAnalysisResultRepository results = new InMemoryAnalysisResultRepository();
        PriceDataset dataset = datasets.save(execution.condition().stockId(), execution.dataset());
        AnalysisResult result = results.save(execution.condition().id(), dataset.id(), execution.calculatedAnalysis());

        verify(validator).validate(CALCULATED, "msa-core-v1");
        assertEquals(CONDITION_ID, result.conditionId());
        assertEquals(dataset.id(), result.datasetId());
        assertEquals(DATASET, datasets.findById(result.datasetId()).orElseThrow().dataset());
        assertEquals(CALCULATED, results.findById(result.id()).orElseThrow().calculatedAnalysis());
    }

    @Test
    void fetchFailureStopsBeforeAnalyzeAndValidation() {
        givenSavedConditionAndStock();
        PythonApiException failure = pythonFailure(502, "PROVIDER_UNAVAILABLE");
        when(pythonClient.fetchPrices(any(FetchPricesRequest.class))).thenThrow(failure);

        assertSame(failure, assertThrows(PythonApiException.class, () -> service.analyze(CONDITION_ID, REQUEST_ID)));

        verify(pythonClient).fetchPrices(any(FetchPricesRequest.class));
        verifyNoMoreInteractions(pythonClient);
        verifyNoInteractions(validator);
    }

    @Test
    void analyzeFailureStopsBeforeValidation() {
        givenSavedConditionAndStock();
        when(pythonClient.fetchPrices(any(FetchPricesRequest.class))).thenReturn(DATASET);
        PythonApiException failure = pythonFailure(422, "INSUFFICIENT_STATES");
        when(pythonClient.analyze(any(AnalyzeInput.class))).thenThrow(failure);

        assertSame(failure, assertThrows(PythonApiException.class, () -> service.analyze(CONDITION_ID, REQUEST_ID)));

        verifyNoInteractions(validator);
    }

    @Test
    void validatorFailurePropagatesToCaller() {
        givenSavedConditionAndStock();
        when(pythonClient.fetchPrices(any(FetchPricesRequest.class))).thenReturn(DATASET);
        when(pythonClient.analyze(any(AnalyzeInput.class))).thenReturn(CALCULATED);
        CalculationInvariantFailedException failure = new CalculationInvariantFailedException("Invalid row sum");
        doThrow(failure).when(validator).validate(CALCULATED, "msa-core-v1");

        assertSame(failure, assertThrows(CalculationInvariantFailedException.class,
                () -> service.analyze(CONDITION_ID, REQUEST_ID)));
    }

    @Test
    void missingConditionStopsBeforeStockLookupAndPythonCalls() {
        ConditionNotFoundException failure = new ConditionNotFoundException(CONDITION_ID);
        when(conditionService.findById(CONDITION_ID)).thenThrow(failure);

        assertSame(failure, assertThrows(ConditionNotFoundException.class,
                () -> service.analyze(CONDITION_ID, REQUEST_ID)));

        verifyNoInteractions(stockRepository, pythonClient, validator);
    }

    @Test
    void missingStockStopsBeforePythonCalls() {
        when(conditionService.findById(CONDITION_ID)).thenReturn(CONDITION);
        when(stockRepository.findAll()).thenReturn(List.of(
                new StockSummary("9001", "TEST", "Other stock", "XTKS", "JPY", "Asia/Tokyo")));

        assertThrows(StockNotFoundException.class, () -> service.analyze(CONDITION_ID, REQUEST_ID));

        verifyNoInteractions(pythonClient, validator);
    }

    private void givenSavedConditionAndStock() {
        when(conditionService.findById(CONDITION_ID)).thenReturn(CONDITION);
        when(stockRepository.findAll()).thenReturn(List.of(
                new StockSummary("9001", "TEST", "Other stock", "XTKS", "JPY", "Asia/Tokyo"), STOCK));
    }

    private PythonApiException pythonFailure(int status, String code) {
        return new PythonApiException(status, new ApiErrorResponse(code, "Python request failed", REQUEST_ID, Map.of()), null);
    }
}
