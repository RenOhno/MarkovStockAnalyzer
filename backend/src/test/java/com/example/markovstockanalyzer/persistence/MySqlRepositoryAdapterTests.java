package com.example.markovstockanalyzer.persistence;

import com.example.markovstockanalyzer.MarkovStockAnalyzerApplication;
import com.example.markovstockanalyzer.client.PythonAnalysisClient;
import com.example.markovstockanalyzer.dto.request.*;
import com.example.markovstockanalyzer.dto.response.*;
import com.example.markovstockanalyzer.exception.*;
import com.example.markovstockanalyzer.mapper.*;
import com.example.markovstockanalyzer.model.*;
import com.example.markovstockanalyzer.repository.*;
import com.example.markovstockanalyzer.service.*;
import com.example.markovstockanalyzer.support.BacktestFixtures;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mysql")
@Testcontainers
class MySqlRepositoryAdapterTests {
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("msa_adapters").withUsername("msa_test").withPassword(UUID.randomUUID().toString())
            .withCommand("--default-time-zone=+00:00", "--character-set-server=utf8mb4", "--log-bin-trust-function-creators=1");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", MYSQL::getJdbcUrl); registry.add("DB_USERNAME", MYSQL::getUsername);
        registry.add("DB_PASSWORD", MYSQL::getPassword); registry.add("MARKET_CALENDAR_VERSION", () -> "4.11.1");
    }
    @Autowired ApplicationContext context;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired StockRepository stocks;
    @Autowired ConditionRepository conditions;
    @Autowired PriceDatasetRepository datasets;
    @Autowired AnalysisResultRepository analyses;
    @Autowired BacktestResultRepository backtests;
    @Autowired AnalysisService analysisService;
    @Autowired AnalysisResultWriter analysisWriter;
    @Autowired BacktestResultWriter backtestWriter;
    @Autowired AnalysisResultResponseMapper analysisMapper;
    @Autowired BacktestResultResponseMapper backtestMapper;
    @MockitoBean PythonAnalysisClient python;
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
    private ConditionResponse condition;

    @BeforeEach void prepare() {
        reset(python);
        for (String table : List.of("backtest_predictions", "transition_probabilities", "backtest_results", "analysis_results",
                "stock_prices", "price_datasets", "analysis_conditions")) { jdbc.update("DELETE FROM " + table); }
        condition = conditions.save(conditionRequest());
    }
    @Test void mysqlProfileHasOneAdapterPerBoundaryAndTransactionalWriterBeans() {
        for (Class<?> type : List.of(StockRepository.class, ConditionRepository.class, PriceDatasetRepository.class,
                AnalysisResultRepository.class, BacktestResultRepository.class)) {
            assertEquals(1, context.getBeansOfType(type).size());
        }
        assertTrue(context.getBeansOfType(InMemoryStockRepository.class).isEmpty());
        assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(analysisWriter));
        assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(backtestWriter));
    }
    @Test void stockApiReadsSeedAndHonorsEnabledFlag() throws Exception {
        assertTrue(stocks.existsById("7203")); assertFalse(stocks.existsById("invalid"));
        mvc.perform(get("/api/stocks")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        jdbc.update("UPDATE stocks SET enabled=FALSE WHERE id=9001");
        try {
            assertFalse(stocks.existsById("9001")); assertEquals(1, stocks.findAll().size());
        } finally { jdbc.update("UPDATE stocks SET enabled=TRUE WHERE id=9001"); }
        verifyNoInteractions(python);
    }
    @Test void conditionPostAndGetRestoreAllValuesFromDatabase() throws Exception {
        var response = mvc.perform(post("/api/conditions").contentType("application/json").content(json.writeValueAsString(conditionRequest())))
                .andExpect(status().isCreated()).andReturn();
        var saved = json.readValue(response.getResponse().getContentAsString(), ConditionResponse.class);
        assertEquals(saved, conditions.findById(saved.id()).orElseThrow());
        mvc.perform(get("/api/conditions/{id}", saved.id())).andExpect(status().isOk())
                .andExpect(content().json(json.writeValueAsString(saved)));
        mvc.perform(get("/api/conditions").param("stockId", "7203")).andExpect(jsonPath("$.length()").value(2));
        verifyNoInteractions(python);
    }
    @Test void datasetAndPricesRoundTripWithSnapshotIdentityAndNullableVolume() {
        var payload = payload(Instant.now().minusSeconds(60));
        var saved = datasets.save(condition.stockId(), payload);
        assertEquals(saved, datasets.findById(saved.id()).orElseThrow());
        assertEquals(payload, saved.dataset());
        assertEquals(payload.prices().size(), count("stock_prices"));
        assertEquals(payload.prices().size(), jdbc.queryForObject("SELECT row_count FROM price_datasets WHERE id=?", Integer.class, saved.id()));
        assertNull(saved.dataset().prices().getFirst().volume());
        // Snapshot identity is independent of subsequent master changes.
        jdbc.update("UPDATE stocks SET ticker='CHANGED',time_zone='UTC' WHERE id=7203");
        try { assertEquals("7203.T", datasets.findById(saved.id()).orElseThrow().dataset().ticker()); }
        finally { jdbc.update("UPDATE stocks SET ticker='7203.T',time_zone='Asia/Tokyo' WHERE id=7203"); }
    }
    @Test void equalContentHashCanBeSavedAsIndependentSnapshots() {
        var first = datasets.save(condition.stockId(), payload(Instant.now().minusSeconds(120)));
        var second = datasets.save(condition.stockId(), payload(Instant.now().minusSeconds(60)));
        assertNotEquals(first.id(), second.id()); assertEquals(first.dataset().contentSha256(), second.dataset().contentSha256());
        assertEquals(2, count("price_datasets"));
    }
    @ParameterizedTest @ValueSource(strings = {"start", "end", "duplicate", "order", "negative", "precision"})
    void invalidDatasetRowsAreRejectedBeforeSaving(String invalid) {
        var payload = payload(Instant.now().minusSeconds(60)); var rows = new ArrayList<>(payload.prices());
        LocalDate start = payload.coverageStart(), end = payload.coverageEnd();
        switch (invalid) {
            case "start" -> start = start.minusDays(1);
            case "end" -> end = end.plusDays(1);
            case "duplicate" -> rows.add(rows.getLast());
            case "order" -> Collections.reverse(rows);
            case "negative" -> rows.set(0, new PricePoint(start, "-1", "99.0000000000", null));
            case "precision" -> rows.set(0, new PricePoint(start, "100.00000000001", "99.0000000000", null));
        }
        var bad = copy(payload, start, end, payload.metadata(), rows);
        assertThrows(CalculationInvariantFailedException.class, () -> datasets.save(condition.stockId(), bad));
        assertEquals(0, count("price_datasets")); assertEquals(0, count("stock_prices"));
    }
    @Test void datasetChildInsertFailureRollsBackParentAndEarlierPrices() {
        trigger("fail_price", "stock_prices", "NEW.trade_date='2025-02-28'");
        try { assertThrows(DataAccessException.class, () -> datasets.save(condition.stockId(), payload(Instant.now()))); }
        finally { jdbc.execute("DROP TRIGGER fail_price"); }
        assertEquals(0, count("price_datasets")); assertEquals(0, count("stock_prices"));
    }
    @Test void analysisWriterStoresNineTransitionsAndSamePublicResponse() throws Exception {
        var execution = new AnalysisExecutionResult(condition, payload(Instant.now()), analysis(false));
        var saved = analysisWriter.save(execution);
        assertEquals(1, count("analysis_results")); assertEquals(9, count("transition_probabilities"));
        assertEquals(analysis(false), analyses.findById(saved.id()).orElseThrow().calculatedAnalysis());
        var dataset = datasets.findById(saved.datasetId()).orElseThrow();
        var expected = analysisMapper.map(saved, dataset);
        mvc.perform(get("/api/analysis/{id}", saved.id())).andExpect(status().isOk())
                .andExpect(content().json(json.writeValueAsString(expected)));
        verifyNoInteractions(python);
    }
    @Test void unavailableAnalysisPersistsNullProbabilitiesEmptyForecastsAndWarnings() throws Exception {
        var saved = analysisWriter.save(new AnalysisExecutionResult(condition, payload(Instant.now()), analysis(true)));
        var restored = analyses.findById(saved.id()).orElseThrow();
        assertEquals(analysis(true), restored.calculatedAnalysis());
        assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM transition_probabilities WHERE probability IS NULL", Integer.class));
        mvc.perform(get("/api/analysis/{id}", saved.id())).andExpect(jsonPath("$.predictionStatus").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.forecasts.length()").value(0));
        verifyNoInteractions(python);
    }
    @Test void analysisLastTransitionFailureRollsBackResultTransitionsAndWriterDataset() {
        trigger("fail_transition", "transition_probabilities", "NEW.from_state='DOWN' AND NEW.to_state='DOWN'");
        try { assertThrows(DataAccessException.class, () -> analysisWriter.save(new AnalysisExecutionResult(condition,
                payload(Instant.now()), analysis(false)))); }
        finally { jdbc.execute("DROP TRIGGER fail_transition"); }
        assertEquals(0, count("analysis_results")); assertEquals(0, count("transition_probabilities"));
        assertEquals(0, count("price_datasets")); assertEquals(0, count("stock_prices"));
    }
    @Test void failedAnalysisWithExistingSnapshotKeepsSnapshotAndRollsBackResult() {
        var dataset = datasets.save(condition.stockId(), payload(Instant.now()));
        trigger("fail_transition", "transition_probabilities", "NEW.from_state='DOWN' AND NEW.to_state='DOWN'");
        try { assertThrows(DataAccessException.class, () -> analysisWriter.save(new AnalysisExecutionResult(condition,
                dataset.dataset(), analysis(false), dataset.id()))); }
        finally { jdbc.execute("DROP TRIGGER fail_transition"); }
        assertEquals(1, count("price_datasets")); assertEquals(0, count("analysis_results")); assertEquals(0, count("transition_probabilities"));
    }
    @Test void writerKeepsAnalysisValidatorBeforeResultPersistence() {
        var valid = analysis(false);
        var invalid = new CalculatedAnalysis(List.of("DOWN", "FLAT", "UP"), valid.asOfDate(), valid.currentState(), valid.sampleCount(),
                valid.transitionCount(), valid.transitionCounts(), valid.transitionMatrix(), valid.predictionStatus(), valid.forecasts(),
                valid.warnings(), valid.engineVersion(), valid.runtime());
        assertThrows(CalculationInvariantFailedException.class, () -> analysisWriter.save(new AnalysisExecutionResult(condition,
                payload(Instant.now()), invalid)));
        assertEquals(0, count("analysis_results")); assertEquals(0, count("price_datasets"));
    }
    @Test void backtestWriterStoresAllPredictionsAndSamePublicResponseAndPages() throws Exception {
        var dataset = datasets.save(condition.stockId(), payload(Instant.now()));
        var saved = backtestWriter.save(backtestExecution(dataset, BacktestFixtures.mixed()));
        assertEquals(1, count("backtest_results")); assertEquals(2, count("backtest_predictions"));
        assertEquals(saved, backtests.findById(saved.id()).orElseThrow());
        var expected = backtestMapper.map(saved, dataset);
        mvc.perform(get("/api/backtest/{id}", saved.id())).andExpect(status().isOk())
                .andExpect(content().json(json.writeValueAsString(expected)));
        mvc.perform(get("/api/backtest/{id}/predictions", saved.id()).param("page", "1").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].status").value("SKIPPED"));
        verifyNoInteractions(python);
    }
    @Test void backtestAllSkippedKeepsNullableMetricsAndPredictionFields() {
        var dataset = datasets.save(condition.stockId(), payload(Instant.now()));
        var saved = backtestWriter.save(backtestExecution(dataset, BacktestFixtures.allSkipped()));
        assertEquals(BacktestFixtures.allSkipped(), backtests.findById(saved.id()).orElseThrow().calculatedBacktest());
    }
    @Test void backtestLastPredictionFailureRollsBackParentAndEarlierPrediction() {
        var dataset = datasets.save(condition.stockId(), payload(Instant.now()));
        trigger("fail_prediction", "backtest_predictions", "NEW.status='SKIPPED'");
        try { assertThrows(DataAccessException.class, () -> backtestWriter.save(backtestExecution(dataset, BacktestFixtures.mixed()))); }
        finally { jdbc.execute("DROP TRIGGER fail_prediction"); }
        assertEquals(0, count("backtest_results")); assertEquals(0, count("backtest_predictions")); assertEquals(1, count("price_datasets"));
    }
    @Test void backtestPredictionCountMismatchRejectedBeforeParentInsert() {
        var dataset = datasets.save(condition.stockId(), payload(Instant.now())); var mixed = BacktestFixtures.mixed();
        var bad = new CalculatedBacktest(mixed.summary(), List.of(BacktestFixtures.scored()), mixed.engineVersion(), mixed.runtime());
        assertThrows(CalculationInvariantFailedException.class, () -> backtestWriter.save(backtestExecution(dataset, bad)));
        assertEquals(0, count("backtest_results"));
    }
    @Test void historyReadsBothStoredResultsAndDatasetFetchedAtWithoutPython() throws Exception {
        var dataset = datasets.save(condition.stockId(), payload(Instant.now().minusSeconds(3600)));
        var analysis = analysisWriter.save(new AnalysisExecutionResult(condition, dataset.dataset(), analysis(false)), dataset.id());
        var backtest = backtestWriter.save(backtestExecution(dataset, BacktestFixtures.mixed()));
        var history = context.getBean(HistoryService.class).findAll("ALL", "7203", 0, 20);
        assertEquals(2, history.totalElements());
        assertEquals(Set.of("ANALYSIS", "BACKTEST"), history.items().stream().map(HistorySummaryResponse::type).collect(java.util.stream.Collectors.toSet()));
        for (var row : history.items()) {
            assertEquals(condition.id().toString(), row.conditionId()); assertEquals(dataset.id().toString(), row.datasetId());
            assertEquals(dataset.dataset().fetchedAt().toInstant(), row.dataFetchedAt());
        }
        assertTrue(history.items().stream().filter(row -> row.type().equals("BACKTEST")).allMatch(row -> row.predictionStatus() == null));
        mvc.perform(get("/api/history").param("type", "ALL")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/analysis/{id}", analysis.id())).andExpect(status().isOk());
        mvc.perform(get("/api/backtest/{id}", backtest.id())).andExpect(status().isOk());
        verifyNoInteractions(python);
    }
    @Test void explicitDatasetReuseDoesNotFetchOrSaveDuplicateEvenWhenExpired() throws Exception {
        var saved = datasets.save(condition.stockId(), payload(Instant.now().minus(Duration.ofDays(3))));
        stubAnalysis();
        var response = postAnalysis(saved.id());
        assertEquals(saved.id().toString(), response.datasetId()); assertEquals(1, count("price_datasets"));
        verify(python, never()).fetchPrices(any());
        var input = org.mockito.ArgumentCaptor.forClass(AnalyzeInput.class); verify(python).analyze(input.capture());
        assertEquals(saved.dataset(), input.getValue().dataset());
    }
    @Test void validCacheUsesLatestMatchingSnapshotWithoutFetchOrDuplicate() throws Exception {
        var first = datasets.save(condition.stockId(), payload(Instant.now().minusSeconds(7200)));
        var latest = datasets.save(condition.stockId(), payload(Instant.now().minusSeconds(60)));
        analysisWriter.save(new AnalysisExecutionResult(condition, first.dataset(), analysis(false)), first.id());
        analysisWriter.save(new AnalysisExecutionResult(condition, latest.dataset(), analysis(false)), latest.id());
        // Newer wrong calendar must not displace the latest matching version.
        var newer = payload(Instant.now().minusSeconds(30)); var metadata = new HashMap<>(newer.metadata()); metadata.put("calendarVersion", "different");
        var other = datasets.save(condition.stockId(), copy(newer, newer.coverageStart(), newer.coverageEnd(), metadata, newer.prices()));
        analysisWriter.save(new AnalysisExecutionResult(condition, other.dataset(), analysis(false)), other.id());
        stubAnalysis();
        assertEquals(latest.id().toString(), postAnalysis(null).datasetId()); assertEquals(3, count("price_datasets"));
        verify(python, never()).fetchPrices(any());
    }
    @Test void expiredCacheFetchesWholeSnapshotSavesBeforeAnalyzeAndUsesSameRequestId() throws Exception {
        var old = datasets.save(condition.stockId(), payload(Instant.now().minus(Duration.ofHours(25))));
        analysisWriter.save(new AnalysisExecutionResult(condition, old.dataset(), analysis(false)), old.id());
        stubFetch();
        when(python.analyze(any())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(2, count("price_datasets")); assertEquals(12, count("stock_prices")); return analysis(false);
        });
        var response = postAnalysis(null); assertNotEquals(old.id().toString(), response.datasetId());
        var fetch = org.mockito.ArgumentCaptor.forClass(FetchPricesRequest.class);
        var input = org.mockito.ArgumentCaptor.forClass(AnalyzeInput.class);
        var order = inOrder(python); order.verify(python).fetchPrices(fetch.capture()); order.verify(python).analyze(input.capture());
        assertEquals(fetch.getValue().requestId(), input.getValue().requestId());
        assertEquals(condition.startDate(), fetch.getValue().startDate()); assertEquals(condition.endDate(), fetch.getValue().endDate());
        assertTrue(fetch.getValue().includePreviousSession()); assertEquals(BacktestFixtures.dataset().metadata(), input.getValue().dataset().metadata());
    }
    @Test void cacheReusesPricesAcrossDifferentConditionsAndSendsTheSelectedCondition() throws Exception {
        var dataset = datasets.save(condition.stockId(), payload(Instant.now().minusSeconds(60)));
        analysisWriter.save(new AnalysisExecutionResult(condition, dataset.dataset(), analysis(false)), dataset.id());
        condition = conditions.save(new CreateConditionRequest("Different thresholds", condition.stockId(), condition.startDate(), condition.endDate(),
                new java.math.BigDecimal("-0.003"), new java.math.BigDecimal("0.003"), 3, "MLE_STRICT", "FULL", null, List.of(1, 3, 5, 10)));
        stubAnalysis();
        assertEquals(dataset.id().toString(), postAnalysis(null).datasetId()); assertEquals(1, count("price_datasets"));
        var input = org.mockito.ArgumentCaptor.forClass(AnalyzeInput.class); verify(python).analyze(input.capture());
        assertEquals(AnalysisCondition.from(condition), input.getValue().condition()); verify(python, never()).fetchPrices(any());
    }
    @Test void cacheCanUseCoverageValidatedByASavedBacktest() throws Exception {
        var dataset = datasets.save(condition.stockId(), payload(Instant.now().minusSeconds(60)));
        backtestWriter.save(backtestExecution(dataset, BacktestFixtures.mixed()));
        stubAnalysis();
        assertEquals(dataset.id().toString(), postAnalysis(null).datasetId()); verify(python, never()).fetchPrices(any());
        assertEquals(1, count("price_datasets"));
    }
    @Test void failedCalculationKeepsFetchSnapshotButDoesNotCertifyItsSessionCoverage() {
        stubFetch(); when(python.analyze(any())).thenThrow(new AnalysisServiceUnavailableException(new java.net.ConnectException()));
        assertThrows(AnalysisServiceUnavailableException.class, () -> analysisService.analyze(condition.id(), "calculation-failure"));
        assertEquals(1, count("price_datasets")); assertEquals(6, count("stock_prices")); assertEquals(0, count("analysis_results"));
        assertTrue(datasets.findReusable(new DatasetCacheCriteria(condition.stockId(), "YFINANCE", "PROVIDER_ADJUSTED_CLOSE",
                "PROVIDER_ADJUSTED_CLOSE_V1", "4.11.1", condition.startDate(), condition.endDate(),
                Instant.now().minus(Duration.ofHours(24)), Instant.now())).isEmpty());
    }
    @ParameterizedTest @ValueSource(strings = {"stock", "provider", "basis", "policy", "calendar", "range", "preceding", "future", "unproven"})
    void cacheRejectsMismatchedIdentityRangeOrFreshness(String mismatch) throws Exception {
        var payload = payload(Instant.now().minusSeconds(60)); var metadata = new HashMap<>(payload.metadata());
        if (mismatch.equals("basis")) {
            var saved = datasets.save(condition.stockId(), payload);
            analysisWriter.save(new AnalysisExecutionResult(condition, saved.dataset(), analysis(false)), saved.id());
            assertTrue(datasets.findReusable(new DatasetCacheCriteria(condition.stockId(), "YFINANCE", "CLOSE",
                    "PROVIDER_ADJUSTED_CLOSE_V1", "4.11.1", condition.startDate(), condition.endDate(),
                    Instant.now().minus(Duration.ofHours(24)), Instant.now())).isEmpty());
            return;
        }
        String stockId = condition.stockId(), provider = payload.provider(), basis = payload.priceBasis(), policy = payload.adjustmentPolicy();
        LocalDate requestedStart = condition.startDate(), requestedEnd = condition.endDate();
        var prices = payload.prices(); var start = payload.coverageStart(); var fetchedAt = payload.fetchedAt();
        switch (mismatch) {
            case "stock" -> stockId = "9001";
            case "provider" -> provider = "OTHER";
            case "policy" -> policy = "OTHER_V1";
            case "calendar" -> metadata.put("calendarVersion", "other");
            case "range" -> requestedEnd = requestedEnd.minusDays(1);
            case "preceding" -> { prices = prices.subList(1, prices.size()); start = prices.getFirst().date(); }
            case "future" -> fetchedAt = Instant.now().plusSeconds(3600).atOffset(ZoneOffset.UTC);
        }
        var candidate = new PriceDatasetPayload(payload.ticker(), payload.exchange(), payload.timeZone(), basis, provider,
                payload.providerVersion(), policy, fetchedAt, start, payload.coverageEnd(), payload.contentSha256(), metadata, prices);
        var saved = datasets.save(stockId, candidate);
        if (!mismatch.equals("unproven")) {
            var verifiedCondition = mismatch.equals("range") ? conditions.save(new CreateConditionRequest(condition.name(), condition.stockId(),
                    requestedStart, requestedEnd, condition.lowerThreshold(), condition.upperThreshold(), condition.stateCount(), condition.estimator(),
                    condition.windowMode(), condition.windowSize(), condition.horizons())) : condition;
            analysisWriter.save(new AnalysisExecutionResult(verifiedCondition, saved.dataset(), analysis(false)), saved.id());
        }
        stubFetch(); stubAnalysis();
        postAnalysis(null); verify(python).fetchPrices(any()); assertEquals(2, count("price_datasets"));
    }
    @Test void cacheBoundaryAllowsExactly24HoursAndHolidayEndWithoutJavaCalendarGuessing() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        var holidayCondition = conditions.save(new CreateConditionRequest(condition.name(), condition.stockId(), condition.startDate(),
                condition.endDate().plusDays(2), condition.lowerThreshold(), condition.upperThreshold(), condition.stateCount(), condition.estimator(),
                condition.windowMode(), condition.windowSize(), condition.horizons()));
        var saved = datasets.save(condition.stockId(), payload(now.minus(Duration.ofHours(24))));
        analysisWriter.save(new AnalysisExecutionResult(holidayCondition, saved.dataset(), analysis(false)), saved.id());
        var criteria = new DatasetCacheCriteria(condition.stockId(), "YFINANCE", "PROVIDER_ADJUSTED_CLOSE", "PROVIDER_ADJUSTED_CLOSE_V1",
                "4.11.1", condition.startDate(), condition.endDate().plusDays(2), now.minus(Duration.ofHours(24)), now);
        assertEquals(saved.id(), datasets.findReusable(criteria).orElseThrow().id());
    }
    @Test void failedFetchDoesNotFallBackToExpiredSnapshotOrAnalyze() {
        var saved = datasets.save(condition.stockId(), payload(Instant.now().minus(Duration.ofHours(25))));
        analysisWriter.save(new AnalysisExecutionResult(condition, saved.dataset(), analysis(false)), saved.id());
        when(python.fetchPrices(any())).thenThrow(new AnalysisServiceUnavailableException(new java.net.ConnectException()));
        assertThrows(AnalysisServiceUnavailableException.class, () -> analysisService.analyze(condition.id(), "failure-request"));
        verify(python, never()).analyze(any()); assertEquals(1, count("price_datasets")); assertEquals(1, count("analysis_results"));
    }
    @Test void unavailablePostIsSuccessfulAndSaved() throws Exception {
        stubFetch(); when(python.analyze(any())).thenReturn(analysis(true));
        var response = postAnalysis(null); assertEquals("UNAVAILABLE", response.predictionStatus());
        assertEquals(1, count("analysis_results")); assertEquals(9, count("transition_probabilities"));
    }
    @Test void backtestPublicPostAndSeriesHttpCallsRunOutsideDatabaseTransactions() throws Exception {
        var dataset = datasets.save(condition.stockId(), payload(Instant.now()));
        when(python.backtest(any())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); return BacktestFixtures.mixed();
        });
        var request = new CreateBacktestRequest(condition.id(), dataset.id(), BacktestFixtures.REQUEST.testStart(), BacktestFixtures.REQUEST.testEnd(),
                30, "EXPANDING", null, 1);
        mvc.perform(post("/api/backtest").contentType("application/json").content(json.writeValueAsString(request)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.datasetId").value(dataset.id().toString()));
        var saved = analysisWriter.save(new AnalysisExecutionResult(condition, dataset.dataset(), analysis(false)), dataset.id());
        when(python.series(any())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); var input = (SeriesInput) invocation.getArgument(0);
            assertEquals(saved.calculatedAnalysis().engineVersion(), input.requiredEngineVersion());
            assertEquals(dataset.dataset(), input.dataset()); assertEquals(AnalysisCondition.from(condition), input.condition());
            return new CalculatedSeries(dataset.dataset().priceBasis(), List.of(new SeriesPoint(condition.startDate(), "100.0000000000",
                    "99.0000000000", 0.0, "FLAT")), "msa-core-v1");
        });
        mvc.perform(get("/api/analysis/{id}/series", saved.id())).andExpect(status().isOk()).andExpect(jsonPath("$.points.length()").value(1));
        verify(python, never()).fetchPrices(any());
    }
    @Test void databaseFailureReturnsSafe503WithRequestIdWithoutSqlCredentialsOrTrace() throws Exception {
        trigger("fail_condition", "analysis_conditions", "TRUE");
        try {
            var response = mvc.perform(post("/api/conditions").header("X-Request-Id", "database-error-001")
                            .contentType("application/json").content(json.writeValueAsString(conditionRequest())))
                    .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("PERSISTENCE_SERVICE_UNAVAILABLE"))
                    .andExpect(jsonPath("$.requestId").value("database-error-001")).andReturn().getResponse().getContentAsString();
            assertFalse(response.contains("INSERT")); assertFalse(response.contains("jdbc:")); assertFalse(response.contains("private"));
            assertFalse(response.contains(MYSQL.getPassword())); assertFalse(response.contains("stack"));
        } finally { jdbc.execute("DROP TRIGGER fail_condition"); }
    }
    @Test void separateSpringContextRestoresConditionsDatasetsResultsAndCache() {
        var dataset = datasets.save(condition.stockId(), payload(Instant.now().minusSeconds(60)));
        var analysis = analysisWriter.save(new AnalysisExecutionResult(condition, dataset.dataset(), analysis(false)), dataset.id());
        var backtest = backtestWriter.save(backtestExecution(dataset, BacktestFixtures.mixed()));
        var expectedAnalysis = analysisMapper.map(analysis, dataset); var expectedBacktest = backtestMapper.map(backtest, dataset);
        var app = new SpringApplication(MarkovStockAnalyzerApplication.class); app.setWebApplicationType(WebApplicationType.NONE);
        app.setAdditionalProfiles("mysql");
        try (var restarted = app.run("--DB_URL=" + MYSQL.getJdbcUrl(), "--DB_USERNAME=" + MYSQL.getUsername(), "--DB_PASSWORD=" + MYSQL.getPassword(),
                "--MARKET_CALENDAR_VERSION=4.11.1", "--spring.main.banner-mode=off", "--logging.level.root=WARN")) {
            assertEquals(condition, restarted.getBean(ConditionRepository.class).findById(condition.id()).orElseThrow());
            assertEquals(dataset, restarted.getBean(PriceDatasetRepository.class).findById(dataset.id()).orElseThrow());
            assertEquals(expectedAnalysis, restarted.getBean(AnalysisResultQueryService.class).findById(analysis.id()));
            assertEquals(expectedBacktest, restarted.getBean(BacktestResultQueryService.class).findById(backtest.id()));
            assertEquals(2, restarted.getBean(HistoryService.class).findAll("ALL", null, 0, 20).totalElements());
            // Read the cache boundary directly: even a regression cannot initiate external HTTP.
            var criteria = new DatasetCacheCriteria(condition.stockId(), "YFINANCE", "PROVIDER_ADJUSTED_CLOSE",
                    "PROVIDER_ADJUSTED_CLOSE_V1", "4.11.1", condition.startDate(), condition.endDate(),
                    Instant.now().minus(Duration.ofHours(24)), Instant.now());
            assertEquals(dataset.id(), restarted.getBean(PriceDatasetRepository.class).findReusable(criteria).orElseThrow().id());
        }
        verifyNoInteractions(python);
    }
    private AnalysisResultResponse postAnalysis(Long datasetId) throws Exception {
        var response = mvc.perform(post("/api/analysis").contentType("application/json")
                        .content(json.writeValueAsString(new CreateAnalysisRequest(condition.id(), datasetId))))
                .andExpect(status().isCreated()).andExpect(header().exists("Location")).andReturn();
        return json.readValue(response.getResponse().getContentAsString(), AnalysisResultResponse.class);
    }
    private void stubAnalysis() {
        when(python.analyze(any())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); return analysis(false);
        });
    }
    private void stubFetch() {
        when(python.fetchPrices(any())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); return payload(Instant.now().minusSeconds(1));
        });
    }
    private CreateConditionRequest conditionRequest() {
        var source = BacktestFixtures.CONDITION;
        return new CreateConditionRequest(source.name(), source.stockId(), source.startDate(), source.endDate(), source.lowerThreshold(),
                source.upperThreshold(), source.stateCount(), source.estimator(), source.windowMode(), source.windowSize(), source.horizons());
    }
    private PriceDatasetPayload payload(Instant fetchedAt) {
        var source = BacktestFixtures.dataset();
        return new PriceDatasetPayload(source.ticker(), source.exchange(), source.timeZone(), source.priceBasis(), source.provider(),
                source.providerVersion(), source.adjustmentPolicy(), fetchedAt.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC),
                source.coverageStart(), source.coverageEnd(), source.contentSha256(), source.metadata(), source.prices());
    }
    private PriceDatasetPayload copy(PriceDatasetPayload source, LocalDate start, LocalDate end, Map<String, Object> metadata, List<PricePoint> prices) {
        return new PriceDatasetPayload(source.ticker(), source.exchange(), source.timeZone(), source.priceBasis(), source.provider(),
                source.providerVersion(), source.adjustmentPolicy(), source.fetchedAt(), start, end, source.contentSha256(), metadata, prices);
    }
    private CalculatedAnalysis analysis(boolean unavailable) {
        var counts = unavailable ? List.of(List.of(30, 0, 0), List.of(0, 0, 0), List.of(0, 0, 0))
                : List.of(List.of(10, 0, 0), List.of(0, 10, 0), List.of(0, 0, 10));
        List<List<Double>> matrix = unavailable ? List.of(List.of(1.0, 0.0, 0.0), Arrays.asList(null, null, null), Arrays.asList(null, null, null))
                : List.of(List.of(1.0, 0.0, 0.0), List.of(0.0, 1.0, 0.0), List.of(0.0, 0.0, 1.0));
        return new CalculatedAnalysis(List.of("UP", "FLAT", "DOWN"), LocalDate.parse("2025-02-28"), "UP", 31, 30, counts, matrix,
                unavailable ? "UNAVAILABLE" : "AVAILABLE", unavailable ? List.of() : List.of(1, 3, 5, 10).stream()
                .map(horizon -> new ForecastPayload(horizon, List.of(1.0, 0.0, 0.0))).toList(), unavailable ?
                List.of(new AnalysisWarning("ZERO_ROW_UNESTIMATED", List.of("FLAT", "DOWN"))) : List.of(), "msa-core-v1", BacktestFixtures.runtime());
    }
    private BacktestExecutionResult backtestExecution(PriceDataset dataset, CalculatedBacktest calculated) {
        return new BacktestExecutionResult(condition, dataset, BacktestFixtures.REQUEST.evaluation(), calculated);
    }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    private void trigger(String name, String table, String condition) {
        jdbc.execute("CREATE TRIGGER " + name + " BEFORE INSERT ON " + table + " FOR EACH ROW BEGIN IF " + condition
                + " THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='private SQL jdbc:mysql password stack'; END IF; END");
    }
}
