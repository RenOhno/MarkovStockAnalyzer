package com.example.markovstockanalyzer.controller;

import com.example.markovstockanalyzer.client.PythonAnalysisClient;
import com.example.markovstockanalyzer.dto.request.*;
import com.example.markovstockanalyzer.dto.response.*;
import com.example.markovstockanalyzer.exception.*;
import com.example.markovstockanalyzer.mapper.*;
import com.example.markovstockanalyzer.model.*;
import com.example.markovstockanalyzer.repository.*;
import com.example.markovstockanalyzer.service.*;
import com.example.markovstockanalyzer.support.BacktestFixtures;
import com.example.markovstockanalyzer.validation.BacktestResultValidator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class BacktestControllerTests {
    @Mock private PythonAnalysisClient python;
    private InMemoryConditionRepository conditions;
    private InMemoryPriceDatasetRepository datasets;
    private InMemoryBacktestResultRepository results;
    private PriceDataset dataset;
    private MockMvc mvc;
    private final JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();

    @BeforeEach void setUp() {
        conditions = new InMemoryConditionRepository();
        var c = BacktestFixtures.CONDITION;
        conditions.save(new CreateConditionRequest(c.name(), c.stockId(), c.startDate(), c.endDate(), c.lowerThreshold(),
                c.upperThreshold(), c.stateCount(), c.estimator(), c.windowMode(), c.windowSize(), c.horizons()));
        datasets = new InMemoryPriceDatasetRepository();
        dataset = datasets.save(c.stockId(), BacktestFixtures.dataset());
        results = new InMemoryBacktestResultRepository();
        var service = new BacktestService(conditions, datasets, python, new BacktestResultValidator());
        var mapper = new BacktestResultResponseMapper(new AnalysisResultResponseMapper());
        mvc = MockMvcBuilders.standaloneSetup(new BacktestController(service, new BacktestResultWriter(results),
                new BacktestResultQueryService(results, datasets, mapper))).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test void posts201WithLocationReferencesMetadataAndOneGeneratedRequestId() throws Exception {
        when(python.backtest(any(BacktestInput.class))).thenReturn(BacktestFixtures.mixed());
        MvcResult response = mvc.perform(post("/api/backtest").header("X-Request-Id", "client-id")
                        .contentType(MediaType.APPLICATION_JSON).content(json(BacktestFixtures.request(dataset.id()))))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/backtest/1"))
                .andExpect(jsonPath("$.id").value("1")).andExpect(jsonPath("$.conditionId").value("101"))
                .andExpect(jsonPath("$.datasetId").value(dataset.id().toString()))
                .andExpect(jsonPath("$.testStart").value("2025-02-20")).andExpect(jsonPath("$.testEnd").value("2025-02-21"))
                .andExpect(jsonPath("$.horizon").value(1)).andExpect(jsonPath("$.eligibleCount").value(2))
                .andExpect(jsonPath("$.predictedCount").value(1)).andExpect(jsonPath("$.correctCount").value(1))
                .andExpect(jsonPath("$.skippedCount").value(1)).andExpect(jsonPath("$.coverage").value(0.5))
                .andExpect(jsonPath("$.dataSource.calendarName").value("XTKS"))
                .andExpect(jsonPath("$.dataSource.calendarVersion").value("4.11.1"))
                .andExpect(jsonPath("$.dataSource.fetchedAt").value("2026-10-06T01:02:03Z"))
                .andExpect(jsonPath("$.provenance.gitCommit").value("b".repeat(40)))
                .andExpect(jsonPath("$.provenance.dependencyVersions.numpy").value("2.2.0")).andReturn();
        ArgumentCaptor<BacktestInput> input = ArgumentCaptor.forClass(BacktestInput.class);
        verify(python).backtest(input.capture()); verifyNoMoreInteractions(python);
        String requestId = response.getResponse().getHeader("X-Request-Id");
        assertDoesNotThrow(() -> UUID.fromString(requestId)); assertNotEquals("client-id", requestId);
        assertEquals(requestId, input.getValue().requestId());
        assertEquals("msa-core-v1", input.getValue().engineVersion());
        assertEquals(AnalysisCondition.from(BacktestFixtures.CONDITION), input.getValue().condition());
        assertSame(dataset.dataset(), input.getValue().dataset());
        assertEquals(BacktestFixtures.REQUEST.evaluation(), input.getValue().evaluation());
        var saved = results.findById(1L).orElseThrow();
        assertEquals(101L, saved.conditionId()); assertEquals(dataset.id(), saved.datasetId());
        assertEquals(30, saved.minTrainStates()); assertEquals("EXPANDING", saved.trainingMode()); assertNull(saved.windowSize());
        assertEquals(BacktestFixtures.mixed(), saved.calculatedBacktest());
        assertTrue(datasets.findById(dataset.id() + 1).isEmpty());
        assertFalse(response.getResponse().getContentAsString().contains("runtime-secret"));
        assertFalse(response.getResponse().getContentAsString().contains("private"));
    }

    @Test void allSkippedIsSuccessfulAndKeepsNullableMetrics() throws Exception {
        when(python.backtest(any(BacktestInput.class))).thenReturn(BacktestFixtures.allSkipped());
        mvc.perform(post("/api/backtest").contentType(MediaType.APPLICATION_JSON).content(json(BacktestFixtures.request(dataset.id()))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.predictedCount").value(0))
                .andExpect(jsonPath("$.coverage").value(0.0))
                .andExpect(jsonPath("$.metrics.accuracy").value(org.hamcrest.Matchers.nullValue()));
        assertEquals(BacktestFixtures.allSkipped(), results.findById(1L).orElseThrow().calculatedBacktest());
    }

    @Test void getsSavedResultWithoutReexecutingPythonOrReplacingSource() throws Exception {
        var saved = save();
        datasets.save("9001", BacktestFixtures.dataset());
        mvc.perform(get("/api/backtest/{id}", saved.id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.id().toString()))
                .andExpect(jsonPath("$.conditionId").value("101"))
                .andExpect(jsonPath("$.datasetId").value(dataset.id().toString()))
                .andExpect(jsonPath("$.dataSource.contentSha256").value("a".repeat(64)))
                .andExpect(jsonPath("$.provenance.normalizationVersion").value("NORMALIZATION_V1"));
        verifyNoInteractions(python);
        assertSame(saved, results.findById(saved.id()).orElseThrow());
    }

    @Test void getsStoredScoredAndSkippedPredictionsWithDefaultPage() throws Exception {
        var saved = save();
        mvc.perform(get("/api/backtest/{id}/predictions", saved.id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].status").value("SCORED"))
                .andExpect(jsonPath("$.items[0].predictedState").value("UP"))
                .andExpect(jsonPath("$.items[1].status").value("SKIPPED"))
                .andExpect(jsonPath("$.items[1].predictedState").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items[1].probabilities").value(org.hamcrest.Matchers.nullValue()));
        verifyNoInteractions(python);
    }

    @Test void pagesPredictionsAndHandlesEmptyAndHugeOffsets() throws Exception {
        var saved = save();
        mvc.perform(get("/api/backtest/{id}/predictions", saved.id()).param("page", "1").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].status").value("SKIPPED"));
        for (String page : List.of("2", "2147483647")) {
            mvc.perform(get("/api/backtest/{id}/predictions", saved.id()).param("page", page).param("size", "100"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(2));
        }
        verifyNoInteractions(python);
    }

    @ParameterizedTest @CsvSource({"-1,20","0,0","0,101","abc,20","0,abc","2147483648,20","1.5,20"})
    void rejectsInvalidPagingAs400(String page, String size) throws Exception {
        mvc.perform(get("/api/backtest/999/predictions").param("page", page).param("size", size))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAGINATION"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
        verifyNoInteractions(python);
    }

    @ParameterizedTest @ValueSource(strings = {"/api/backtest/999", "/api/backtest/999/predictions"})
    void missingSavedBacktestReturns404(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BACKTEST_RESULT_NOT_FOUND")).andExpect(jsonPath("$.requestId").isNotEmpty());
        verifyNoInteractions(python);
    }

    @ParameterizedTest(name = "{0}") @MethodSource("invalidRequests")
    void rejectsInputConstraintsAs422WithoutPython(String name, Consumer<Input> change) throws Exception {
        Input input = new Input(dataset.id()); change.accept(input);
        mvc.perform(post("/api/backtest").contentType(MediaType.APPLICATION_JSON).content(json(input.request())))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INVALID_BACKTEST_REQUEST"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
        verifyNoInteractions(python); assertTrue(results.findById(1L).isEmpty());
    }
    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                invalid("condition required", i -> i.conditionId = null), invalid("dataset required", i -> i.datasetId = null),
                invalid("positive dataset", i -> i.datasetId = 0L), invalid("min train", i -> i.min = 29),
                invalid("training mode", i -> i.mode = "ROLLING"), invalid("window", i -> i.window = 10),
                invalid("horizon", i -> i.horizon = 3), invalid("start required", i -> i.start = null),
                invalid("end required", i -> i.end = null), invalid("reverse evaluation", i -> i.start = i.end.plusDays(1)),
                invalid("start outside condition", i -> i.start = LocalDate.parse("2025-01-05")),
                invalid("end outside condition", i -> i.end = LocalDate.parse("2025-03-01")));
    }
    static Arguments invalid(String name, Consumer<Input> change) { return Arguments.of(name, change); }

    @Test void requiresExplicitNullableWindowSizeProperty() throws Exception {
        ObjectNode body = (ObjectNode) jsonMapper.readTree(json(BacktestFixtures.request(dataset.id())));
        body.remove("windowSize");
        mvc.perform(post("/api/backtest").contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_JSON"));
        verifyNoInteractions(python);
    }

    @ParameterizedTest @ValueSource(strings = {"stock", "basis", "preceding", "range"})
    void rejectsDatasetConditionMismatchAs409(String problem) throws Exception {
        var base = BacktestFixtures.dataset();
        var start = base.coverageStart(); var end = base.coverageEnd(); var prices = base.prices();
        if ("preceding".equals(problem)) { start = BacktestFixtures.CONDITION.startDate(); prices = prices.subList(1, prices.size()); }
        if ("range".equals(problem)) { end = BacktestFixtures.CONDITION.startDate().minusDays(1); prices = List.of(prices.getFirst()); }
        var altered = new PriceDatasetPayload(base.ticker(), base.exchange(), base.timeZone(),
                "basis".equals(problem) ? "CLOSE" : base.priceBasis(), base.provider(), base.providerVersion(), base.adjustmentPolicy(),
                base.fetchedAt(), start, end, base.contentSha256(), base.metadata(), prices);
        var selected = datasets.save("stock".equals(problem) ? "9001" : "7203", altered);
        mvc.perform(post("/api/backtest").contentType(MediaType.APPLICATION_JSON).content(json(BacktestFixtures.request(selected.id()))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DATASET_CONDITION_MISMATCH"));
        verifyNoInteractions(python);
    }

    @ParameterizedTest @CsvSource({"condition,CONDITION_NOT_FOUND","dataset,PRICE_DATASET_NOT_FOUND"})
    void missingInputsReturn404(String missing, String code) throws Exception {
        Input input = new Input(dataset.id());
        if ("condition".equals(missing)) { input.conditionId = 999L; } else { input.datasetId = 999L; }
        mvc.perform(post("/api/backtest").contentType(MediaType.APPLICATION_JSON).content(json(input.request())))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(code));
        verifyNoInteractions(python);
    }

    @ParameterizedTest @CsvSource({"422,INSUFFICIENT_TRAINING_DATA","409,DATASET_CONDITION_MISMATCH",
            "429,TOO_MANY_ANALYSES","504,PROVIDER_TIMEOUT","500,CALCULATION_INVARIANT_FAILED"})
    void preservesPythonErrorsSafelyAndDoesNotSave(int statusCode, String code) throws Exception {
        when(python.backtest(any(BacktestInput.class))).thenThrow(new PythonApiException(statusCode,
                new ApiErrorResponse(code, "Backtest request failed", "python-backtest-error", Map.of("required", 30, "actual", 18)), null));
        mvc.perform(post("/api/backtest").contentType(MediaType.APPLICATION_JSON).content(json(BacktestFixtures.request(dataset.id()))))
                .andExpect(status().is(statusCode)).andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value("Backtest request failed"))
                .andExpect(jsonPath("$.requestId").value("python-backtest-error"))
                .andExpect(jsonPath("$.details.required").value(30)).andExpect(jsonPath("$.details.actual").value(18));
        assertTrue(results.findById(1L).isEmpty());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void connectionAndTimeoutUse503And504WithoutPrivateCause(boolean timeout) throws Exception {
        Throwable cause = timeout ? new SocketTimeoutException("/private/secret") : new ConnectException("/private/secret");
        when(python.backtest(any(BacktestInput.class))).thenThrow(new AnalysisServiceUnavailableException(cause));
        MvcResult response = mvc.perform(post("/api/backtest").contentType(MediaType.APPLICATION_JSON)
                        .content(json(BacktestFixtures.request(dataset.id()))))
                .andExpect(status().is(timeout ? 504 : 503))
                .andExpect(jsonPath("$.code").value(timeout ? "PROVIDER_TIMEOUT" : "ANALYSIS_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.requestId").isNotEmpty()).andReturn();
        assertFalse(response.getResponse().getContentAsString().contains("private"));
        assertFalse(response.getResponse().getContentAsString().contains("secret"));
        assertTrue(results.findById(1L).isEmpty());
    }

    @Test void invalidCalculatedResultStopsBeforeSaveAndUses500InvariantCode() throws Exception {
        var good = BacktestFixtures.mixed(); var s = good.summary();
        var bad = new CalculatedBacktest(new BacktestSummary(s.testStart(), s.testEnd(), 1, 0, 1, 1, 1, 0.5, s.metrics()),
                good.predictions(), good.engineVersion(), good.runtime());
        when(python.backtest(any(BacktestInput.class))).thenReturn(bad);
        MvcResult response = mvc.perform(post("/api/backtest").contentType(MediaType.APPLICATION_JSON)
                        .content(json(BacktestFixtures.request(dataset.id()))))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("CALCULATION_INVARIANT_FAILED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty()).andReturn();
        ArgumentCaptor<BacktestInput> input = ArgumentCaptor.forClass(BacktestInput.class);
        verify(python).backtest(input.capture());
        assertEquals(input.getValue().requestId(), jsonMapper.readTree(response.getResponse().getContentAsString()).get("requestId").asString());
        assertTrue(results.findById(1L).isEmpty());
    }

    private BacktestResult save() {
        return results.save(101L, dataset.id(), BacktestFixtures.REQUEST.evaluation(), BacktestFixtures.mixed());
    }
    private String json(CreateBacktestRequest request) {
        ObjectNode json = (ObjectNode) jsonMapper.valueToTree(request);
        if (request.conditionId() != null) { json.put("conditionId", request.conditionId().toString()); }
        if (request.datasetId() != null) { json.put("datasetId", request.datasetId().toString()); }
        return jsonMapper.writeValueAsString(json);
    }
    static class Input {
        Long conditionId = 101L, datasetId;
        LocalDate start = BacktestFixtures.REQUEST.testStart(), end = BacktestFixtures.REQUEST.testEnd();
        Integer min = 30, window = null, horizon = 1;
        String mode = "EXPANDING";
        Input(Long datasetId) { this.datasetId = datasetId; }
        CreateBacktestRequest request() { return new CreateBacktestRequest(conditionId, datasetId, start, end, min, mode, window, horizon); }
    }
}
