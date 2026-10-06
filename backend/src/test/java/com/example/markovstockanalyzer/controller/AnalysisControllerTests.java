package com.example.markovstockanalyzer.controller;

import com.example.markovstockanalyzer.client.PythonAnalysisClient;
import com.example.markovstockanalyzer.dto.request.AnalyzeInput;
import com.example.markovstockanalyzer.dto.request.CreateConditionRequest;
import com.example.markovstockanalyzer.dto.request.FetchPricesRequest;
import com.example.markovstockanalyzer.dto.request.SeriesInput;
import com.example.markovstockanalyzer.dto.request.AnalysisCondition;
import com.example.markovstockanalyzer.dto.response.AnalysisWarning;
import com.example.markovstockanalyzer.dto.response.CalculatedAnalysis;
import com.example.markovstockanalyzer.dto.response.CalculatedSeries;
import com.example.markovstockanalyzer.dto.response.SeriesPoint;
import com.example.markovstockanalyzer.dto.response.ConditionResponse;
import com.example.markovstockanalyzer.dto.response.ForecastPayload;
import com.example.markovstockanalyzer.dto.response.PriceDatasetPayload;
import com.example.markovstockanalyzer.dto.response.PricePoint;
import com.example.markovstockanalyzer.exception.AnalysisServiceUnavailableException;
import com.example.markovstockanalyzer.exception.ApiErrorResponse;
import com.example.markovstockanalyzer.exception.ApiExceptionHandler;
import com.example.markovstockanalyzer.exception.PythonApiException;
import com.example.markovstockanalyzer.mapper.AnalysisResultResponseMapper;
import com.example.markovstockanalyzer.model.AnalysisResult;
import com.example.markovstockanalyzer.model.PriceDataset;
import com.example.markovstockanalyzer.repository.AnalysisResultRepository;
import com.example.markovstockanalyzer.repository.InMemoryAnalysisResultRepository;
import com.example.markovstockanalyzer.repository.InMemoryConditionRepository;
import com.example.markovstockanalyzer.repository.InMemoryPriceDatasetRepository;
import com.example.markovstockanalyzer.repository.InMemoryStockRepository;
import com.example.markovstockanalyzer.service.AnalysisResultWriter;
import com.example.markovstockanalyzer.service.AnalysisResultQueryService;
import com.example.markovstockanalyzer.service.AnalysisService;
import com.example.markovstockanalyzer.service.AnalysisSeriesService;
import com.example.markovstockanalyzer.service.ConditionService;
import com.example.markovstockanalyzer.validation.AnalysisResultValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AnalysisControllerTests {
    private static final String INTERNAL_TOKEN = "b0f02498e65c40c49978c516e4160e38";
    @Mock
    private PythonAnalysisClient pythonClient;
    private InMemoryPriceDatasetRepository datasets;
    private InMemoryAnalysisResultRepository results;
    private InMemoryConditionRepository conditionRepository;
    private AnalysisService service;
    private ConditionResponse condition;
    private MockMvc mvc;
    private final JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();

    @BeforeEach
    void setUp() {
        datasets = new InMemoryPriceDatasetRepository();
        results = new InMemoryAnalysisResultRepository();
        InMemoryStockRepository stocks = new InMemoryStockRepository();
        conditionRepository = new InMemoryConditionRepository();
        ConditionService conditions = new ConditionService(conditionRepository, stocks);
        condition = conditions.create(new CreateConditionRequest(
                "Analysis test", "7203", LocalDate.parse("2025-01-06"), LocalDate.parse("2025-02-19"),
                new BigDecimal("-0.005"), new BigDecimal("0.005"), 3, "MLE_STRICT", "FULL", null,
                List.of(1, 3, 5, 10)));
        service = new AnalysisService(conditions, stocks, datasets, pythonClient, new AnalysisResultValidator());
        mvc = mockMvc(new AnalysisResultWriter(datasets, results));
    }

    @Test
    void getsSeriesFromSavedConditionAndDatasetWithOneRequestIdAndPublicFieldsOnly() throws Exception {
        AnalysisResult saved = saveForSeries("msa-core-v1");
        PriceDataset dataset = datasets.findById(saved.datasetId()).orElseThrow();
        conditionRepository.save(new CreateConditionRequest("Newer condition", "9001",
                condition.startDate(), condition.endDate(), new BigDecimal("-0.007"), new BigDecimal("0.01"),
                3, "MLE_STRICT", "FULL", null, List.of(1, 3, 5, 10)));
        datasets.save("9001", payload());
        when(pythonClient.series(any(SeriesInput.class))).thenReturn(calculatedSeries("msa-core-v1"));

        MvcResult response = mvc.perform(get("/api/analysis/{id}/series", saved.id()).header("X-Request-Id", "client-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value(saved.id().toString()))
                .andExpect(jsonPath("$.priceBasis").value("PROVIDER_ADJUSTED_CLOSE"))
                .andExpect(jsonPath("$.points.length()").value(2))
                .andExpect(jsonPath("$.points[0].date").value("2025-01-06"))
                .andExpect(jsonPath("$.points[0].close").value("100.0000000000"))
                .andExpect(jsonPath("$.points[0].adjustedClose").value("100.0000000000"))
                .andExpect(jsonPath("$.points[0].returnValue").value(0.0))
                .andExpect(jsonPath("$.points[0].state").value("FLAT"))
                .andExpect(jsonPath("$.engineVersion").doesNotExist())
                .andReturn();

        ArgumentCaptor<SeriesInput> input = ArgumentCaptor.forClass(SeriesInput.class);
        verify(pythonClient).series(input.capture());
        verifyNoMoreInteractions(pythonClient);
        assertEquals(AnalysisCondition.from(condition), input.getValue().condition());
        assertSame(dataset.dataset(), input.getValue().dataset());
        assertEquals(saved.calculatedAnalysis().engineVersion(), input.getValue().requiredEngineVersion());
        assertEquals("msa-core-v1", input.getValue().engineVersion());
        String requestId = response.getResponse().getHeader("X-Request-Id");
        assertDoesNotThrow(() -> UUID.fromString(requestId));
        assertNotEquals("client-id", requestId);
        assertEquals(requestId, input.getValue().requestId());
        var json = jsonMapper.readTree(response.getResponse().getContentAsString());
        assertEquals(3, json.size());
        assertEquals(5, json.get("points").get(0).size());
        assertEquals(jsonMapper.valueToTree(calculatedSeries("msa-core-v1").points()), json.get("points"));
    }

    @Test
    void missingSeriesAnalysisReturns404WithoutPython() throws Exception {
        mvc.perform(get("/api/analysis/999/series"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ANALYSIS_RESULT_NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
        verifyNoInteractions(pythonClient);
    }

    @Test
    void missingSeriesConditionReturns404WithoutPython() throws Exception {
        PriceDataset dataset = datasets.save(condition.stockId(), payload());
        AnalysisResult saved = results.save(999L, dataset.id(), calculated(false));

        mvc.perform(get("/api/analysis/{id}/series", saved.id()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CONDITION_NOT_FOUND"));
        verifyNoInteractions(pythonClient);
    }

    @Test
    void missingSeriesDatasetReturns404WithoutFetchingReplacement() throws Exception {
        AnalysisResult saved = results.save(condition.id(), 999L, calculated(false));

        mvc.perform(get("/api/analysis/{id}/series", saved.id()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRICE_DATASET_NOT_FOUND"));
        verifyNoInteractions(pythonClient);
    }

    @ParameterizedTest
    @CsvSource({"409,ENGINE_VERSION_UNSUPPORTED,msa-core-v0", "422,INVALID_DATASET,msa-core-v1"})
    void preservesSeriesPythonErrorsAndPassesSavedRequiredVersionWithoutFallback(
            int statusCode, String code, String savedVersion
    ) throws Exception {
        AnalysisResult saved = saveForSeries(savedVersion);
        when(pythonClient.series(any(SeriesInput.class))).thenThrow(new PythonApiException(statusCode,
                new ApiErrorResponse(code, "Series request failed", "python-series-error",
                        Map.of("requiredEngineVersion", savedVersion, "supportedEngineVersion", "msa-core-v1")), null));

        mvc.perform(get("/api/analysis/{id}/series", saved.id()))
                .andExpect(status().is(statusCode)).andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value("Series request failed"))
                .andExpect(jsonPath("$.requestId").value("python-series-error"))
                .andExpect(jsonPath("$.details.requiredEngineVersion").value(savedVersion));

        ArgumentCaptor<SeriesInput> input = ArgumentCaptor.forClass(SeriesInput.class);
        verify(pythonClient).series(input.capture());
        assertEquals(savedVersion, input.getValue().requiredEngineVersion());
        assertEquals("msa-core-v1", input.getValue().engineVersion());
        verifyNoMoreInteractions(pythonClient);
    }

    @Test
    void seriesConnectionFailureReturnsSafe503() throws Exception {
        AnalysisResult saved = saveForSeries("msa-core-v1");
        when(pythonClient.series(any(SeriesInput.class))).thenThrow(new AnalysisServiceUnavailableException(
                new ConnectException("token=secret /private/path")));

        MvcResult response = mvc.perform(get("/api/analysis/{id}/series", saved.id()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ANALYSIS_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.requestId").isNotEmpty()).andReturn();

        assertFalse(response.getResponse().getContentAsString().contains("secret"));
        assertFalse(response.getResponse().getContentAsString().contains("private"));
        verifySeriesErrorRequestId(response);
    }

    @Test
    void seriesTimeoutReturns504WithSameProcessRequestId() throws Exception {
        AnalysisResult saved = saveForSeries("msa-core-v1");
        when(pythonClient.series(any(SeriesInput.class))).thenThrow(new AnalysisServiceUnavailableException(
                new SocketTimeoutException("Read timed out")));

        MvcResult response = mvc.perform(get("/api/analysis/{id}/series", saved.id()))
                .andExpect(status().isGatewayTimeout()).andExpect(jsonPath("$.code").value("PROVIDER_TIMEOUT"))
                .andExpect(jsonPath("$.requestId").isNotEmpty()).andReturn();
        verifySeriesErrorRequestId(response);
    }

    @Test
    void rejectsDifferentReturnedSeriesEngineVersionInsteadOfPublishingNewLogic() throws Exception {
        AnalysisResult saved = saveForSeries("msa-core-v1");
        when(pythonClient.series(any(SeriesInput.class))).thenReturn(calculatedSeries("msa-core-v2"));

        MvcResult response = mvc.perform(get("/api/analysis/{id}/series", saved.id()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ENGINE_VERSION_UNSUPPORTED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty()).andReturn();
        verifySeriesErrorRequestId(response);
    }

    @Test
    void getsSavedAnalysisWithItsOwnDatasetAndProvenanceWithoutPythonOrRevalidation() throws Exception {
        PriceDataset dataset = datasets.save(condition.stockId(), payload());
        CalculatedAnalysis base = calculated(false);
        CalculatedAnalysis historical = new CalculatedAnalysis(
                base.stateOrder(), base.asOfDate(), base.currentState(), base.sampleCount(), base.transitionCount(),
                base.transitionCounts(), base.transitionMatrix(), base.predictionStatus(), base.forecasts(), base.warnings(),
                "msa-core-v0", Map.of("engineVersion", "msa-core-v0", "gitCommit", "b".repeat(40),
                        "pythonVersion", "3.11.9", "numpyVersion", "1.26.4", "pandasVersion", "2.1.4",
                        "normalizationVersion", "NORMALIZATION_SAVED", "configurationVersion", "analysis-config-saved-v1"));
        AnalysisResult saved = results.save(condition.id(), dataset.id(), historical);
        PriceDatasetPayload source = payload();
        // A newer stored dataset must not change the old analysis's source information.
        datasets.save(condition.stockId(), new PriceDatasetPayload(
                source.ticker(), source.exchange(), source.timeZone(), source.priceBasis(), source.provider(), "0.2.99",
                source.adjustmentPolicy(), source.fetchedAt().plusHours(1), source.coverageStart(), source.coverageEnd(),
                "c".repeat(64), Map.of("calendarName", "XTKS", "calendarVersion", "4.12.0"), source.prices()));

        MvcResult response = mvc.perform(get("/api/analysis/{id}", saved.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.id().toString()))
                .andExpect(jsonPath("$.conditionId").value(condition.id().toString()))
                .andExpect(jsonPath("$.datasetId").value(dataset.id().toString()))
                .andExpect(jsonPath("$.engineVersion").value("msa-core-v0"))
                .andExpect(jsonPath("$.createdAt").value(saved.createdAt().toString()))
                .andExpect(jsonPath("$.transitionCounts[0][0]").value(7))
                .andExpect(jsonPath("$.dataSource.provider").value("YFINANCE"))
                .andExpect(jsonPath("$.dataSource.providerVersion").value("0.2.65"))
                .andExpect(jsonPath("$.dataSource.adjustmentPolicy").value("PROVIDER_ADJUSTED_CLOSE_V1"))
                .andExpect(jsonPath("$.dataSource.fetchedAt").value("2026-10-06T01:02:03Z"))
                .andExpect(jsonPath("$.dataSource.coverageStart").value("2024-12-30"))
                .andExpect(jsonPath("$.dataSource.coverageEnd").value("2025-02-19"))
                .andExpect(jsonPath("$.dataSource.contentSha256").value("a".repeat(64)))
                .andExpect(jsonPath("$.dataSource.calendarName").value("XTKS"))
                .andExpect(jsonPath("$.dataSource.calendarVersion").value("4.11.1"))
                .andExpect(jsonPath("$.provenance.engineVersion").value("msa-core-v0"))
                .andExpect(jsonPath("$.provenance.gitCommit").value("b".repeat(40)))
                .andExpect(jsonPath("$.provenance.dependencyVersions.python").value("3.11.9"))
                .andExpect(jsonPath("$.provenance.dependencyVersions.numpy").value("1.26.4"))
                .andExpect(jsonPath("$.provenance.dependencyVersions.pandas").value("2.1.4"))
                .andExpect(jsonPath("$.provenance.normalizationVersion").value("NORMALIZATION_SAVED"))
                .andExpect(jsonPath("$.provenance.configurationVersion").value("analysis-config-saved-v1"))
                .andReturn();

        var json = jsonMapper.readTree(response.getResponse().getContentAsString());
        assertEquals(jsonMapper.valueToTree(historical.transitionMatrix()), json.get("transitionMatrix"));
        assertEquals(jsonMapper.valueToTree(historical.forecasts()), json.get("forecasts"));
        verifyNoInteractions(pythonClient);
        assertSame(saved, results.findById(saved.id()).orElseThrow());
    }

    @Test
    void getsSavedUnavailablePartialResultAs200WithoutPython() throws Exception {
        PriceDataset dataset = datasets.save(condition.stockId(), payload());
        AnalysisResult saved = results.save(condition.id(), dataset.id(), calculated(true));

        MvcResult response = mvc.perform(get("/api/analysis/{id}", saved.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.predictionStatus").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.forecasts").isEmpty())
                .andExpect(jsonPath("$.warnings[0].code").value("ZERO_ROW_UNESTIMATED"))
                .andReturn();

        assertTrue(response.getResponse().getContentAsString().contains("[null,null,null]"));
        verifyNoInteractions(pythonClient);
    }

    @Test
    void missingSavedAnalysisReturnsDedicated404WithRequestId() throws Exception {
        mvc.perform(get("/api/analysis/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ANALYSIS_RESULT_NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        verifyNoInteractions(pythonClient);
    }

    @Test
    void missingReferencedDatasetReturns404WithoutPythonFallback() throws Exception {
        AnalysisResult saved = results.save(condition.id(), 999L, calculated(false));

        mvc.perform(get("/api/analysis/{id}", saved.id()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRICE_DATASET_NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        verifyNoInteractions(pythonClient);
    }

    @Test
    void createsAndSavesAnalysisWithLocationAndOneGeneratedRequestId() throws Exception {
        when(pythonClient.fetchPrices(any(FetchPricesRequest.class))).thenReturn(payload());
        when(pythonClient.analyze(any(AnalyzeInput.class))).thenReturn(calculated(false));

        MvcResult response = mvc.perform(post("/api/analysis").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Request-Id", "client-id").content(request(null)))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/analysis/1"))
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.conditionId").value(condition.id().toString()))
                .andExpect(jsonPath("$.datasetId").value("1"))
                .andExpect(jsonPath("$.predictionStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.dataSource.calendarName").value("XTKS"))
                .andReturn();

        ArgumentCaptor<FetchPricesRequest> fetch = ArgumentCaptor.forClass(FetchPricesRequest.class);
        ArgumentCaptor<AnalyzeInput> analyze = ArgumentCaptor.forClass(AnalyzeInput.class);
        verify(pythonClient).fetchPrices(fetch.capture());
        verify(pythonClient).analyze(analyze.capture());
        String requestId = response.getResponse().getHeader("X-Request-Id");
        assertDoesNotThrow(() -> UUID.fromString(requestId));
        assertNotEquals("client-id", requestId);
        assertEquals(requestId, fetch.getValue().requestId());
        assertEquals(requestId, analyze.getValue().requestId());
        AnalysisResult saved = results.findById(1L).orElseThrow();
        assertEquals(condition.id(), saved.conditionId());
        assertEquals(calculated(false), saved.calculatedAnalysis());
        assertEquals(payload(), datasets.findById(saved.datasetId()).orElseThrow().dataset());
    }

    @Test
    void reusesSelectedDatasetWithoutFetchOrDuplicateDatasetSave() throws Exception {
        PriceDataset saved = datasets.save(condition.stockId(), payload());
        when(pythonClient.analyze(any(AnalyzeInput.class))).thenReturn(calculated(false));

        mvc.perform(post("/api/analysis").contentType(MediaType.APPLICATION_JSON).content(request(saved.id())))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/analysis/1"))
                .andExpect(jsonPath("$.conditionId").value(condition.id().toString()))
                .andExpect(jsonPath("$.datasetId").value(saved.id().toString()));

        ArgumentCaptor<AnalyzeInput> analyze = ArgumentCaptor.forClass(AnalyzeInput.class);
        verify(pythonClient).analyze(analyze.capture());
        verify(pythonClient, never()).fetchPrices(any(FetchPricesRequest.class));
        assertSame(saved.dataset(), analyze.getValue().dataset());
        assertEquals(saved.id(), results.findById(1L).orElseThrow().datasetId());
        assertTrue(datasets.findById(saved.id() + 1).isEmpty());
    }

    @Test
    void savesUnavailablePartialResultAs201WithEmptyForecasts() throws Exception {
        when(pythonClient.fetchPrices(any(FetchPricesRequest.class))).thenReturn(payload());
        when(pythonClient.analyze(any(AnalyzeInput.class))).thenReturn(calculated(true));

        mvc.perform(post("/api/analysis").contentType(MediaType.APPLICATION_JSON).content(request(null)))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/analysis/1"))
                .andExpect(jsonPath("$.predictionStatus").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.forecasts").isEmpty())
                .andExpect(jsonPath("$.warnings[0].code").value("ZERO_ROW_UNESTIMATED"));

        assertEquals(calculated(true), results.findById(1L).orElseThrow().calculatedAnalysis());
    }

    @Test
    void missingConditionReturns404WithGeneratedRequestId() throws Exception {
        mvc.perform(post("/api/analysis").contentType(MediaType.APPLICATION_JSON).content("{\"conditionId\":\"999\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CONDITION_NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
        verifyNoInteractions(pythonClient);
    }

    @Test
    void missingDatasetReturns404WithoutFetch() throws Exception {
        mvc.perform(post("/api/analysis").contentType(MediaType.APPLICATION_JSON).content(request(999L)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRICE_DATASET_NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
        verifyNoInteractions(pythonClient);
    }

    @Test
    void datasetConditionMismatchReturns409WithoutPythonCalls() throws Exception {
        PriceDataset saved = datasets.save("9001", payload());
        mvc.perform(post("/api/analysis").contentType(MediaType.APPLICATION_JSON).content(request(saved.id())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DATASET_CONDITION_MISMATCH"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
        verifyNoInteractions(pythonClient);
    }

    @ParameterizedTest
    @CsvSource({"422,INSUFFICIENT_STATES", "502,PROVIDER_UNAVAILABLE", "504,PROVIDER_TIMEOUT",
            "429,TOO_MANY_ANALYSES", "500,CALCULATION_INVARIANT_FAILED"})
    void preservesPythonStatusAndUnifiedErrorFields(int statusCode, String code) throws Exception {
        when(pythonClient.fetchPrices(any(FetchPricesRequest.class))).thenReturn(payload());
        when(pythonClient.analyze(any(AnalyzeInput.class))).thenThrow(new PythonApiException(statusCode,
                new ApiErrorResponse(code, "Python analysis failed", "python-response-id",
                        Map.of("required", 30, "actual", 18, "context", Map.of("retry", false))),
                new IllegalStateException("/private/stack-trace")));

        mvc.perform(post("/api/analysis").contentType(MediaType.APPLICATION_JSON).content(request(null)))
                .andExpect(status().is(statusCode)).andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value("Python analysis failed"))
                .andExpect(jsonPath("$.requestId").value("python-response-id"))
                .andExpect(jsonPath("$.details.required").value(30))
                .andExpect(jsonPath("$.details.actual").value(18))
                .andExpect(jsonPath("$.details.context.retry").value(false));
        assertTrue(datasets.findById(1L).isEmpty());
        assertTrue(results.findById(1L).isEmpty());
    }

    @Test
    void stoppedPythonReturns503WithoutExposingCause() throws Exception {
        when(pythonClient.fetchPrices(any(FetchPricesRequest.class))).thenThrow(
                new AnalysisServiceUnavailableException(new ConnectException("/private/path token=secret")));

        MvcResult response = mvc.perform(post("/api/analysis").contentType(MediaType.APPLICATION_JSON).content(request(null)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ANALYSIS_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.requestId").isNotEmpty()).andReturn();
        assertFalse(response.getResponse().getContentAsString().contains("private"));
        assertFalse(response.getResponse().getContentAsString().contains("secret"));
    }

    @Test
    void transportTimeoutReturns504() throws Exception {
        when(pythonClient.fetchPrices(any(FetchPricesRequest.class))).thenThrow(
                new AnalysisServiceUnavailableException(new SocketTimeoutException("Read timed out")));

        mvc.perform(post("/api/analysis").contentType(MediaType.APPLICATION_JSON).content(request(null)))
                .andExpect(status().isGatewayTimeout()).andExpect(jsonPath("$.code").value("PROVIDER_TIMEOUT"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void invalidCalculationReturns500WithSameProcessRequestIdAndDoesNotSave() throws Exception {
        CalculatedAnalysis valid = calculated(false);
        CalculatedAnalysis invalid = new CalculatedAnalysis(List.of("DOWN", "FLAT", "UP"), valid.asOfDate(),
                valid.currentState(), valid.sampleCount(), valid.transitionCount(), valid.transitionCounts(),
                valid.transitionMatrix(), valid.predictionStatus(), valid.forecasts(), valid.warnings(),
                valid.engineVersion(), valid.runtime());
        when(pythonClient.fetchPrices(any(FetchPricesRequest.class))).thenReturn(payload());
        when(pythonClient.analyze(any(AnalyzeInput.class))).thenReturn(invalid);

        MvcResult response = mvc.perform(post("/api/analysis").contentType(MediaType.APPLICATION_JSON).content(request(null)))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("CALCULATION_INVARIANT_FAILED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty()).andReturn();

        ArgumentCaptor<AnalyzeInput> analyze = ArgumentCaptor.forClass(AnalyzeInput.class);
        verify(pythonClient).analyze(analyze.capture());
        assertEquals(analyze.getValue().requestId(), jsonMapper.readTree(response.getResponse().getContentAsString())
                .get("requestId").asString());
        assertTrue(datasets.findById(1L).isEmpty());
        assertTrue(results.findById(1L).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"conditionId\":\"0\"}", "{\"conditionId\":\"101\",\"datasetId\":\"0\"}"})
    void rejectsMissingOrNonPositiveIds(String body) throws Exception {
        mvc.perform(post("/api/analysis").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.requestId").isNotEmpty());
        verifyNoInteractions(pythonClient);
    }

    @Test
    void omitsSecretDiagnosticsFromPythonError() throws Exception {
        when(pythonClient.fetchPrices(any(FetchPricesRequest.class))).thenThrow(new PythonApiException(502,
                new ApiErrorResponse("PROVIDER_UNAVAILABLE", "token=root-secret at /private/path", "python-id", Map.of(
                        "required", 30, "INTERNAL_API_TOKEN", "root-secret", "internalPath", "/private/path",
                        "echo", INTERNAL_TOKEN,
                        "context", Map.of("actual", 18, "password", "nested-secret"),
                        "traceback", List.of("/private/stack"))), null));

        MvcResult response = mvc.perform(post("/api/analysis").contentType(MediaType.APPLICATION_JSON).content(request(null)))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.requestId").value("python-id"))
                .andExpect(jsonPath("$.details.required").value(30))
                .andExpect(jsonPath("$.details.context.actual").value(18)).andReturn();
        String json = response.getResponse().getContentAsString();
        for (String forbidden : List.of("secret", "token", "private", "password", "traceback", "stack")) {
            assertFalse(json.toLowerCase().contains(forbidden), forbidden);
        }
        assertFalse(json.contains(INTERNAL_TOKEN));
    }

    @Test
    void malformedJsonReturns400WithRequestId() throws Exception {
        mvc.perform(post("/api/analysis").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_JSON"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
        verifyNoInteractions(pythonClient);
    }

    @Test
    void unexpectedSaveFailureReturnsSafe500() throws Exception {
        AnalysisResultRepository failingResults = mock(AnalysisResultRepository.class);
        when(failingResults.save(any(), any(), any())).thenThrow(new IllegalStateException("secret SQL at /private/path"));
        mvc = mockMvc(new AnalysisResultWriter(datasets, failingResults));
        when(pythonClient.fetchPrices(any(FetchPricesRequest.class))).thenReturn(payload());
        when(pythonClient.analyze(any(AnalyzeInput.class))).thenReturn(calculated(false));

        MvcResult response = mvc.perform(post("/api/analysis").contentType(MediaType.APPLICATION_JSON).content(request(null)))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.requestId").isNotEmpty()).andReturn();
        assertFalse(response.getResponse().getContentAsString().contains("secret"));
        assertFalse(response.getResponse().getContentAsString().contains("private"));
    }

    private AnalysisResult saveForSeries(String engineVersion) {
        PriceDataset dataset = datasets.save(condition.stockId(), payload());
        CalculatedAnalysis base = calculated(false);
        CalculatedAnalysis saved = new CalculatedAnalysis(base.stateOrder(), base.asOfDate(), base.currentState(),
                base.sampleCount(), base.transitionCount(), base.transitionCounts(), base.transitionMatrix(),
                base.predictionStatus(), base.forecasts(), base.warnings(), engineVersion, Map.of("engineVersion", engineVersion));
        return results.save(condition.id(), dataset.id(), saved);
    }

    private CalculatedSeries calculatedSeries(String engineVersion) {
        return new CalculatedSeries("PROVIDER_ADJUSTED_CLOSE", List.of(
                new SeriesPoint(LocalDate.parse("2025-01-06"), "100.0000000000", "100.0000000000", 0.0, "FLAT"),
                new SeriesPoint(LocalDate.parse("2025-02-19"), "100.0000000000", "100.0000000000", 0.0, "FLAT")
        ), engineVersion);
    }

    private void verifySeriesErrorRequestId(MvcResult response) throws Exception {
        ArgumentCaptor<SeriesInput> input = ArgumentCaptor.forClass(SeriesInput.class);
        verify(pythonClient).series(input.capture());
        assertEquals(input.getValue().requestId(), jsonMapper.readTree(response.getResponse().getContentAsString())
                .get("requestId").asString());
        verifyNoMoreInteractions(pythonClient);
    }

    private MockMvc mockMvc(AnalysisResultWriter writer) {
        ApiExceptionHandler advice = new ApiExceptionHandler();
        ReflectionTestUtils.setField(advice, "internalApiToken", INTERNAL_TOKEN);
        AnalysisResultResponseMapper mapper = new AnalysisResultResponseMapper();
        AnalysisResultQueryService queryService = new AnalysisResultQueryService(results, datasets, mapper);
        AnalysisSeriesService seriesService = new AnalysisSeriesService(results, conditionRepository, datasets, pythonClient);
        return MockMvcBuilders.standaloneSetup(new AnalysisController(service, writer, mapper, datasets, queryService, seriesService))
                .setControllerAdvice(advice).build();
    }

    private String request(Long datasetId) {
        return "{\"conditionId\":\"" + condition.id() + "\""
                + (datasetId == null ? "" : ",\"datasetId\":\"" + datasetId + "\"") + "}";
    }

    private PriceDatasetPayload payload() {
        return new PriceDatasetPayload("7203.T", "XTKS", "Asia/Tokyo", "PROVIDER_ADJUSTED_CLOSE", "YFINANCE",
                "0.2.65", "PROVIDER_ADJUSTED_CLOSE_V1", OffsetDateTime.parse("2026-10-06T01:02:03Z"),
                LocalDate.parse("2024-12-30"), LocalDate.parse("2025-02-19"), "a".repeat(64),
                Map.of("calendarName", "XTKS", "calendarVersion", "4.11.1"),
                List.of("2024-12-30", "2025-01-06", "2025-02-19").stream()
                        .map(date -> new PricePoint(LocalDate.parse(date), "100.0000000000", "100.0000000000", null)).toList());
    }

    private CalculatedAnalysis calculated(boolean unavailable) {
        List<List<Integer>> counts = unavailable
                ? List.of(List.of(15, 0, 0), List.of(0, 0, 0), List.of(0, 0, 15))
                : List.of(List.of(7, 4, 4), List.of(3, 0, 4), List.of(4, 4, 0));
        List<List<Double>> matrix = unavailable
                ? List.of(List.of(1.0, 0.0, 0.0), Arrays.asList(null, null, null), List.of(0.0, 0.0, 1.0))
                : List.of(List.of(7.0 / 15, 4.0 / 15, 4.0 / 15), List.of(3.0 / 7, 0.0, 4.0 / 7), List.of(0.5, 0.5, 0.0));
        List<ForecastPayload> forecasts = unavailable ? List.of() : List.of(1, 3, 5, 10).stream()
                .map(horizon -> new ForecastPayload(horizon, List.of(0.4, 0.2, 0.4))).toList();
        return new CalculatedAnalysis(List.of("UP", "FLAT", "DOWN"), LocalDate.parse("2025-02-19"), "UP", 31, 30,
                counts, matrix, unavailable ? "UNAVAILABLE" : "AVAILABLE", forecasts,
                unavailable ? List.of(new AnalysisWarning("ZERO_ROW_UNESTIMATED", List.of("FLAT"))) : List.of(),
                "msa-core-v1", Map.of("engineVersion", "msa-core-v1"));
    }
}
