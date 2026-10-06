package com.example.markovstockanalyzer.controller;

import com.example.markovstockanalyzer.client.PythonAnalysisClient;
import com.example.markovstockanalyzer.dto.request.CreateConditionRequest;
import com.example.markovstockanalyzer.dto.response.*;
import com.example.markovstockanalyzer.exception.ApiExceptionHandler;
import com.example.markovstockanalyzer.model.*;
import com.example.markovstockanalyzer.repository.*;
import com.example.markovstockanalyzer.service.HistoryService;
import com.example.markovstockanalyzer.support.BacktestFixtures;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class HistoryControllerTests {
    @Mock private PythonAnalysisClient python;
    private InMemoryAnalysisResultRepository analyses;
    private InMemoryBacktestResultRepository backtests;
    private InMemoryPriceDatasetRepository datasets;
    private InMemoryConditionRepository conditions;
    private AnalysisResult analysis;
    private BacktestResult backtest;
    private ConditionResponse analysisCondition, backtestCondition;
    private PriceDataset analysisDataset, backtestDataset;
    private MockMvc mvc;
    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @BeforeEach void setUp() {
        conditions = new InMemoryConditionRepository();
        var c = BacktestFixtures.CONDITION;
        analysisCondition = conditions.save(new CreateConditionRequest("Saved analysis condition", "7203",
                c.startDate(), c.endDate(), c.lowerThreshold(), c.upperThreshold(), c.stateCount(),
                c.estimator(), c.windowMode(), c.windowSize(), c.horizons()));
        backtestCondition = conditions.save(new CreateConditionRequest("Saved backtest condition", "9001",
                c.startDate(), c.endDate(), c.lowerThreshold(), c.upperThreshold(), c.stateCount(),
                c.estimator(), c.windowMode(), c.windowSize(), c.horizons()));
        datasets = new InMemoryPriceDatasetRepository();
        analysisDataset = datasets.save("7203", BacktestFixtures.dataset());
        var p = BacktestFixtures.dataset();
        Map<String, Object> metadata = new HashMap<>(p.metadata()); metadata.put("ticker", "TEST");
        backtestDataset = datasets.save("9001", new PriceDatasetPayload("TEST", p.exchange(), p.timeZone(), p.priceBasis(),
                p.provider(), p.providerVersion(), p.adjustmentPolicy(), p.fetchedAt().plusHours(2), p.coverageStart(),
                p.coverageEnd(), p.contentSha256(), metadata, p.prices()));
        analyses = new InMemoryAnalysisResultRepository();
        var calculated = new CalculatedAnalysis(List.of("UP", "FLAT", "DOWN"), c.endDate(), "UP", 31, 30,
                List.of(List.of(15, 0, 0), List.of(0, 0, 0), List.of(0, 0, 15)),
                List.of(List.of(1.0, 0.0, 0.0), Arrays.asList(null, null, null), List.of(0.0, 0.0, 1.0)),
                "UNAVAILABLE", List.of(), List.of(new AnalysisWarning("ZERO_ROW_UNESTIMATED", List.of("FLAT"))),
                "msa-core-v0", Map.of("internalPath", "/private", "INTERNAL_API_TOKEN", "hidden-secret"));
        analysis = analyses.save(analysisCondition.id(), analysisDataset.id(), calculated);
        backtests = new InMemoryBacktestResultRepository();
        backtest = backtests.save(backtestCondition.id(), backtestDataset.id(), BacktestFixtures.REQUEST.evaluation(), BacktestFixtures.mixed());
        mvc = buildMvc();
    }

    @AfterEach void neverCallsPython() { verifyNoInteractions(python); }

    @Test void defaultAllReturnsBothKindsAndAllSummaryFieldsFromSavedRecords() throws Exception {
        MvcResult response = mvc.perform(get("/api/history")).andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.items.length()").value(2)).andReturn();
        JsonNode root = mapper.readTree(response.getResponse().getContentAsString());
        assertEquals(4, root.size());
        JsonNode a = item(root, "ANALYSIS"), b = item(root, "BACKTEST");
        assertEquals(12, a.size()); assertEquals(12, b.size());
        assertEquals(analysis.id().toString(), a.get("id").asString());
        assertEquals(analysisCondition.id().toString(), a.get("conditionId").asString());
        assertEquals("7203", a.get("stockId").asString());
        assertEquals("Saved analysis condition", a.get("conditionName").asString());
        assertEquals(analysisCondition.startDate().toString(), a.get("startDate").asString());
        assertEquals(analysisCondition.endDate().toString(), a.get("endDate").asString());
        assertEquals(analysisDataset.id().toString(), a.get("datasetId").asString());
        assertEquals("UNAVAILABLE", a.get("predictionStatus").asString());
        assertEquals("msa-core-v0", a.get("engineVersion").asString());
        assertEquals(analysis.createdAt().toString(), a.get("createdAt").asString());
        assertEquals(analysisDataset.dataset().fetchedAt().toInstant().toString(), a.get("dataFetchedAt").asString());
        assertEquals(backtest.id().toString(), b.get("id").asString());
        assertEquals(backtestCondition.id().toString(), b.get("conditionId").asString());
        assertEquals("9001", b.get("stockId").asString());
        assertEquals("Saved backtest condition", b.get("conditionName").asString());
        assertEquals(backtest.testStart().toString(), b.get("startDate").asString());
        assertEquals(backtest.testEnd().toString(), b.get("endDate").asString());
        assertEquals(backtestDataset.id().toString(), b.get("datasetId").asString());
        assertTrue(b.has("predictionStatus")); assertTrue(b.get("predictionStatus").isNull());
        assertEquals("msa-core-v1", b.get("engineVersion").asString());
        assertEquals(backtest.createdAt().toString(), b.get("createdAt").asString());
        assertEquals(backtestDataset.dataset().fetchedAt().toInstant().toString(), b.get("dataFetchedAt").asString());
        assertFalse(response.getResponse().getContentAsString().contains("hidden-secret"));
        assertFalse(response.getResponse().getContentAsString().contains("private"));
    }

    @ParameterizedTest @ValueSource(strings = {"ANALYSIS", "BACKTEST"})
    void filtersEachType(String type) throws Exception {
        mvc.perform(get("/api/history").param("type", type)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].type").value(type));
    }

    @Test void explicitAllAndStockFilterUseConditionStockId() throws Exception {
        mvc.perform(get("/api/history").param("type", "ALL").param("stockId", "7203")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].type").value("ANALYSIS"))
                .andExpect(jsonPath("$.items[0].stockId").value("7203"));
        mvc.perform(get("/api/history").param("type", "BACKTEST").param("stockId", "9001")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].stockId").value("9001"));
        mvc.perform(get("/api/history").param("type", "ANALYSIS").param("stockId", "9001")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test void pagingHasFilteredTotalsAndSupportsEmptyAndHugePages() throws Exception {
        mvc.perform(get("/api/history").param("page", "1").param("size", "1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.items.length()").value(1));
        for (String page : List.of("2", "2147483647")) {
            mvc.perform(get("/api/history").param("page", page).param("size", "100")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalElements").value(2));
        }
        mvc.perform(get("/api/history").param("stockId", "7203").param("page", "1").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test void unknownStockReturnsEmptyPage() throws Exception {
        mvc.perform(get("/api/history").param("stockId", "unknown")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test void emptyRepositoriesReturnEmptyPage() throws Exception {
        analyses = new InMemoryAnalysisResultRepository();
        backtests = new InMemoryBacktestResultRepository();
        mvc = buildMvc();
        mvc.perform(get("/api/history")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.page").value(0));
    }

    @Test void readsSavedDatasetDateEvenAfterNewerFetchSnapshotIsStoredAndDoesNotWriteResults() throws Exception {
        var p = BacktestFixtures.dataset();
        datasets.save("7203", new PriceDatasetPayload(p.ticker(), p.exchange(), p.timeZone(), p.priceBasis(), p.provider(),
                p.providerVersion(), p.adjustmentPolicy(), p.fetchedAt().plusHours(8), p.coverageStart(), p.coverageEnd(),
                p.contentSha256(), p.metadata(), p.prices()));
        mvc.perform(get("/api/history").param("type", "ANALYSIS")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].dataFetchedAt").value("2026-10-06T01:02:03Z"));
        assertSame(analysis, analyses.findById(analysis.id()).orElseThrow());
        assertSame(backtest, backtests.findById(backtest.id()).orElseThrow());
        assertEquals(1, analyses.findAll().size()); assertEquals(1, backtests.findAll().size());
    }

    @ParameterizedTest @ValueSource(strings = {"OTHER", "analysis", "all", "", " ALL "})
    void invalidTypeIs400(String type) throws Exception {
        mvc.perform(get("/api/history").param("type", type)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_HISTORY_TYPE")).andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @ParameterizedTest @CsvSource({"-1,20", "0,0", "0,101", "abc,20", "0,abc", "2147483648,20", "1.5,20"})
    void invalidPagingIs400(String page, String size) throws Exception {
        mvc.perform(get("/api/history").param("page", page).param("size", size)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGINATION")).andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    private JsonNode item(JsonNode page, String type) {
        for (JsonNode item : page.get("items")) {
            if (type.equals(item.get("type").asString())) { return item; }
        }
        throw new AssertionError("Missing history type " + type);
    }

    private MockMvc buildMvc() {
        return MockMvcBuilders.standaloneSetup(new HistoryController(new HistoryService(analyses, backtests, conditions, datasets)))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }
}
