package com.example.markovstockanalyzer.persistence;

import com.example.markovstockanalyzer.persistence.entity.*;
import com.example.markovstockanalyzer.persistence.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("mysql")
@Testcontainers(disabledWithoutDocker = true)
class MySqlPersistenceTests {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("msa_persistence").withUsername("msa_test").withPassword(UUID.randomUUID().toString())
            .withCommand("--default-time-zone=+00:00", "--character-set-server=utf8mb4");
    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry properties) {
        properties.add("DB_URL", MYSQL::getJdbcUrl);
        properties.add("DB_USERNAME", MYSQL::getUsername);
        properties.add("DB_PASSWORD", MYSQL::getPassword);
    }
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Flyway flyway;
    @Autowired private StockJpaRepository stocks;
    @Autowired private PriceDatasetJpaRepository datasets;
    @Autowired private StockPriceJpaRepository prices;
    @Autowired private AnalysisConditionJpaRepository conditions;
    @Autowired private AnalysisResultJpaRepository analyses;
    @Autowired private TransitionProbabilityJpaRepository transitions;
    @Autowired private BacktestResultJpaRepository backtests;
    @Autowired private BacktestPredictionJpaRepository predictions;
    @Autowired private PlatformTransactionManager transactionManager;
    @PersistenceContext private EntityManager entityManager;
    private long datasetId, conditionId, analysisId, backtestId;

    @BeforeEach void clearTestRowsWithoutDisablingConstraints() {
        for (String table : List.of("backtest_predictions", "transition_probabilities", "backtest_results",
                "analysis_results", "stock_prices", "price_datasets", "analysis_conditions")) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("DELETE FROM stocks WHERE id NOT IN (7203, 9001)");
    }

    @Test void flywayCreatesEightInnoDbUtf8TablesIndexesAndUtcSession() {
        assertEquals(2, flyway.info().applied().length);
        assertDoesNotThrow(() -> flyway.validate());
        Set<String> expected = Set.of("stocks", "price_datasets", "stock_prices", "analysis_conditions",
                "analysis_results", "transition_probabilities", "backtest_results", "backtest_predictions");
        var rows = jdbc.queryForList("SELECT TABLE_NAME, ENGINE, TABLE_COLLATION FROM information_schema.TABLES"
                + " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME <> 'flyway_schema_history'");
        assertEquals(expected, rows.stream().map(row -> (String) row.get("TABLE_NAME")).collect(java.util.stream.Collectors.toSet()));
        rows.forEach(row -> { assertEquals("InnoDB", row.get("ENGINE")); assertTrue(((String) row.get("TABLE_COLLATION")).startsWith("utf8mb4_")); });
        assertEquals("+00:00", jdbc.queryForObject("SELECT @@session.time_zone", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT @@foreign_key_checks", Integer.class));
        Set<String> indexes = new HashSet<>(jdbc.queryForList("SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS"
                + " WHERE TABLE_SCHEMA=DATABASE()", String.class));
        assertTrue(indexes.containsAll(Set.of("uk_stocks_exchange_ticker", "ix_dataset_lookup", "ix_condition_stock_created",
                "ix_analysis_condition_created", "ix_analysis_created", "ix_analysis_dataset",
                "ix_backtest_condition_created", "ix_backtest_created", "ix_backtest_dataset")));
        assertTrue(jdbc.queryForList("SELECT DELETE_RULE FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()",
                String.class).stream().allMatch(rule -> Set.of("RESTRICT", "NO ACTION").contains(rule)));
    }

    @Test void seedsOnlyExistingRegisteredStocksAndNoPrices() {
        assertEquals(2L, stocks.count());
        var toyota = stocks.findById(7203L).orElseThrow(); var test = stocks.findById(9001L).orElseThrow();
        assertEquals("7203.T", toyota.getTicker()); assertEquals("Toyota Motor", toyota.getName());
        assertEquals("TEST", test.getTicker()); assertEquals("Synthetic Test Stock", test.getName());
        for (var stock : List.of(toyota, test)) {
            assertEquals("XTKS", stock.getExchange()); assertEquals("JPY", stock.getCurrency());
            assertEquals("Asia/Tokyo", stock.getTimeZone()); assertTrue(stock.getEnabled());
        }
        assertEquals(0L, datasets.count()); assertEquals(0L, prices.count());
    }

    @Test void foreignKeysRejectAllMissingParentsIncludingAnalysisAndPredictionReferences() {
        fixture();
        violation(1452, "UPDATE price_datasets SET stock_id=999999 WHERE id=?", datasetId);
        violation(1452, "UPDATE analysis_conditions SET stock_id=999999 WHERE id=?", conditionId);
        violation(1452, "UPDATE analysis_results SET condition_id=999999 WHERE id=?", analysisId);
        violation(1452, "UPDATE analysis_results SET dataset_id=999999 WHERE id=?", analysisId);
        violation(1452, "UPDATE backtest_results SET condition_id=999999 WHERE id=?", backtestId);
        violation(1452, "UPDATE backtest_results SET dataset_id=999999 WHERE id=?", backtestId);
        violation(1452, "UPDATE transition_probabilities SET analysis_id=999999 WHERE analysis_id=?", analysisId);
        violation(1452, "UPDATE backtest_predictions SET backtest_id=999999 WHERE backtest_id=?", backtestId);
        violation(1452, "INSERT INTO stock_prices(dataset_id,trade_date,close,adjusted_close) VALUES(999999,'2025-01-06',100,99)");
    }

    @Test void foreignKeysProtectReferencedHistoryFromDeletion() {
        fixture();
        violation(1451, "DELETE FROM stocks WHERE id=7203");
        violation(1451, "DELETE FROM analysis_conditions WHERE id=?", conditionId);
        violation(1451, "DELETE FROM price_datasets WHERE id=?", datasetId);
        violation(1451, "DELETE FROM analysis_results WHERE id=?", analysisId);
        violation(1451, "DELETE FROM backtest_results WHERE id=?", backtestId);
    }

    @Test void stockExchangeTickerIsUniqueButDatasetContentHashIsNot() {
        violation(1062, "INSERT INTO stocks(ticker,name,exchange,currency,time_zone) VALUES('7203.T','Duplicate','XTKS','JPY','Asia/Tokyo')");
        long first = dataset(); long second = dataset();
        assertNotEquals(first, second);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM price_datasets WHERE content_sha256=?", Integer.class, "a".repeat(64)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "UPDATE stocks SET enabled=2 WHERE id=7203",
        "UPDATE price_datasets SET price_basis='CLOSE'",
        "UPDATE price_datasets SET coverage_start='2026-01-01'",
        "UPDATE price_datasets SET row_count=1",
        "UPDATE analysis_conditions SET state_count=4",
        "UPDATE analysis_conditions SET end_date='2024-01-01'",
        "UPDATE analysis_conditions SET lower_threshold=-1",
        "UPDATE analysis_conditions SET upper_threshold=1",
        "UPDATE analysis_conditions SET lower_threshold=upper_threshold",
        "UPDATE analysis_conditions SET estimator='OTHER'",
        "UPDATE analysis_conditions SET window_mode='ROLLING'",
        "UPDATE analysis_conditions SET window_size=1",
        "UPDATE analysis_conditions SET horizons='{}'",
        "UPDATE analysis_conditions SET horizons='[1,3]'",
        "UPDATE analysis_conditions SET schema_version=0",
        "UPDATE analysis_results SET current_state='NONE'",
        "UPDATE analysis_results SET sample_count=29,transition_count=28",
        "UPDATE analysis_results SET transition_count=29",
        "UPDATE analysis_results SET prediction_status='OTHER'",
        "UPDATE analysis_results SET forecasts='{}'",
        "UPDATE transition_probabilities SET from_state='NONE'",
        "UPDATE transition_probabilities SET to_state='NONE'",
        "UPDATE transition_probabilities SET probability=1.5",
        "UPDATE backtest_results SET test_end='2024-01-01'",
        "UPDATE backtest_results SET horizon=3",
        "UPDATE backtest_results SET min_train_states=29",
        "UPDATE backtest_results SET training_mode='FULL'",
        "UPDATE backtest_results SET window_size=1",
        "UPDATE backtest_results SET eligible_count=0",
        "UPDATE backtest_results SET eligible_count=1001",
        "UPDATE backtest_results SET skipped_count=1",
        "UPDATE backtest_results SET correct_count=2",
        "UPDATE backtest_predictions SET train_start='2026-01-01'",
        "UPDATE backtest_predictions SET train_end='2025-02-18'",
        "UPDATE backtest_predictions SET origin_date=target_date",
        "UPDATE backtest_predictions SET actual_state='NONE'",
        "UPDATE backtest_predictions SET predicted_state='NONE'",
        "UPDATE backtest_predictions SET majority_state='NONE'",
        "UPDATE backtest_predictions SET persistence_state='NONE'",
        "UPDATE backtest_predictions SET status='OTHER'",
        "UPDATE backtest_predictions SET status='SKIPPED'",
        "UPDATE backtest_predictions SET predicted_state=NULL",
        "UPDATE backtest_predictions SET probabilities=NULL",
        "UPDATE backtest_predictions SET skip_code='OTHER'",
        "UPDATE stock_prices SET close=0"
    })
    void checkConstraintsAreEnforcedByMysql(String sql) {
        fixture(); jdbc.update("INSERT INTO stock_prices(dataset_id,trade_date,close,adjusted_close) VALUES(?,'2025-01-06',100,99)", datasetId);
        violation(3819, sql);
    }

    @Test void notNullJsonSyntaxAndUnsignedVolumeRemainDatabaseConstraints() {
        fixture();
        violation(1048, "UPDATE analysis_results SET runtime=NULL");
        violation(1048, "UPDATE price_datasets SET metadata=NULL");
        violation(3140, "UPDATE price_datasets SET metadata='{not-json}'");
        jdbc.update("INSERT INTO stock_prices(dataset_id,trade_date,close,adjusted_close,volume) VALUES(?,'2025-01-06',100,99,NULL)", datasetId);
        violation(1264, "UPDATE stock_prices SET volume=-1");
    }

    @Test void stockPricesCompositePrimaryKeyIncludesDatasetAndTradeDate() {
        long a = dataset(), b = dataset();
        String sql = "INSERT INTO stock_prices(dataset_id,trade_date,close,adjusted_close) VALUES(?,'2025-01-06',100,99)";
        jdbc.update(sql, a); violation(1062, sql, a); jdbc.update(sql, b);
        jdbc.update("INSERT INTO stock_prices(dataset_id,trade_date,close,adjusted_close) VALUES(?,'2025-01-07',100,99)", a);
        assertEquals(3L, prices.count());
    }

    @Test void transitionsCompositePrimaryKeyIncludesAnalysisFromAndToState() {
        fixture();
        violation(1062, "INSERT INTO transition_probabilities(analysis_id,from_state,to_state,transition_count,probability) VALUES(?,'UP','UP',30,1)", analysisId);
        jdbc.update("INSERT INTO transition_probabilities(analysis_id,from_state,to_state,transition_count,probability) VALUES(?,'UP','DOWN',0,0)", analysisId);
        long another = analysis(conditionId, datasetId);
        jdbc.update("INSERT INTO transition_probabilities(analysis_id,from_state,to_state,transition_count,probability) VALUES(?,'UP','UP',30,1)", another);
        assertEquals(3L, transitions.count());
    }

    @Test void predictionPrimaryKeyAndScoredSkippedNullableShapesArePreserved() {
        fixture();
        violation(1062, predictionSql(), backtestId);
        jdbc.update("INSERT INTO backtest_predictions(backtest_id,target_date,origin_date,train_start,train_end,actual_state,status,skip_code)"
                + " VALUES(?,'2025-02-21','2025-02-20','2025-01-06','2025-02-20','DOWN','SKIPPED','ZERO_ROW_UNESTIMATED')", backtestId);
        assertEquals("ARRAY", jdbc.queryForObject("SELECT JSON_TYPE(probabilities) FROM backtest_predictions WHERE status='SCORED'", String.class));
        assertNull(jdbc.queryForObject("SELECT probabilities FROM backtest_predictions WHERE status='SKIPPED'", String.class));
        violation(3819, "UPDATE backtest_predictions SET skip_code=NULL WHERE status='SKIPPED'");
    }

    @Test void jpaPersistsAllThreeAggregatesAndRawJsonWithinOneTransaction() {
        List<Long> ids = new TransactionTemplate(transactionManager).execute(status -> persistGraph());
        assertEquals(2L, prices.count()); assertEquals(9L, transitions.count()); assertEquals(2L, predictions.count());
        assertEquals("OBJECT", jdbc.queryForObject("SELECT JSON_TYPE(metadata) FROM price_datasets WHERE id=?", String.class, ids.get(1)));
        assertEquals("ARRAY", jdbc.queryForObject("SELECT JSON_TYPE(horizons) FROM analysis_conditions WHERE id=?", String.class, ids.get(0)));
        assertEquals("ARRAY", jdbc.queryForObject("SELECT JSON_TYPE(forecasts) FROM analysis_results WHERE id=?", String.class, ids.get(2)));
        assertEquals(Instant.parse("2026-10-06T01:02:03.123456Z"), datasets.findById(ids.get(1)).orElseThrow().getFetchedAt());
        assertTrue(prices.findById(new StockPriceId(ids.get(1), LocalDate.parse("2025-01-06"))).isPresent());
        assertTrue(transitions.findById(new TransitionProbabilityId(ids.get(2), "UP", "DOWN")).isPresent());
        assertTrue(predictions.findById(new BacktestPredictionId(ids.get(3), LocalDate.parse("2025-02-21"))).isPresent());
    }

    @Test void aTransactionRollbackRemovesAllParentAndChildRows() {
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactionManager).execute(status -> {
            persistGraph(); throw new IllegalStateException("Rollback probe");
        }));
        assertEquals(0L, datasets.count()); assertEquals(0L, prices.count()); assertEquals(0L, conditions.count());
        assertEquals(0L, analyses.count()); assertEquals(0L, transitions.count()); assertEquals(0L, backtests.count());
        assertEquals(0L, predictions.count());
    }

    private List<Long> persistGraph() {
        var stock = stocks.getReferenceById(7203L);
        var condition = new AnalysisConditionEntity(); condition.setStock(stock); condition.setName("DB fixture");
        condition.setStartDate(LocalDate.parse("2025-01-06")); condition.setEndDate(LocalDate.parse("2025-02-28"));
        condition.setLowerThreshold(new BigDecimal("-0.005")); condition.setUpperThreshold(new BigDecimal("0.005"));
        condition.setEstimator("MLE_STRICT"); condition.setWindowMode("FULL"); condition.setHorizons("[1,3,5,10]");
        conditions.saveAndFlush(condition);
        var dataset = new PriceDatasetEntity(); dataset.setStock(stock); dataset.setProvider("FIXTURE"); dataset.setProviderVersion("1");
        dataset.setPriceBasis("PROVIDER_ADJUSTED_CLOSE"); dataset.setAdjustmentPolicy("PROVIDER_ADJUSTED_CLOSE_V1");
        dataset.setFetchedAt(Instant.parse("2026-10-06T01:02:03.123456Z")); dataset.setCoverageStart(LocalDate.parse("2025-01-06"));
        dataset.setCoverageEnd(LocalDate.parse("2025-01-07")); dataset.setRowCount(2); dataset.setContentSha256("a".repeat(64));
        dataset.setMetadata("{\"schemaVersion\":1}");
        for (String date : List.of("2025-01-06", "2025-01-07")) {
            var price = new StockPriceEntity(); price.setId(new StockPriceId(null, LocalDate.parse(date)));
            price.setClose(new BigDecimal("100.0000000000")); price.setAdjustedClose(new BigDecimal("99.0000000000")); dataset.addPrice(price);
        }
        datasets.saveAndFlush(dataset);
        var analysis = new AnalysisResultEntity(); analysis.setCondition(condition); analysis.setDataset(dataset);
        analysis.setAsOfDate(LocalDate.parse("2025-02-19")); analysis.setCurrentState("UP"); analysis.setSampleCount(31);
        analysis.setTransitionCount(30); analysis.setPredictionStatus("AVAILABLE"); analysis.setForecasts("[]");
        analysis.setQuality("{}"); analysis.setEngineVersion("msa-core-v1"); analysis.setRuntime("{}"); analysis.setResultSha256("b".repeat(64));
        for (String from : List.of("UP", "FLAT", "DOWN")) {
            for (String to : List.of("UP", "FLAT", "DOWN")) {
                var transition = new TransitionProbabilityEntity(); transition.setId(new TransitionProbabilityId(null, from, to));
                transition.setTransitionCount(from.equals(to) ? 10 : 0); transition.setProbability(from.equals(to) ? 1.0 : 0.0);
                analysis.addTransition(transition);
            }
        }
        analyses.saveAndFlush(analysis);
        var backtest = new BacktestResultEntity(); backtest.setCondition(condition); backtest.setDataset(dataset);
        backtest.setTestStart(LocalDate.parse("2025-02-20")); backtest.setTestEnd(LocalDate.parse("2025-02-21"));
        backtest.setMinTrainStates(30); backtest.setTrainingMode("EXPANDING"); backtest.setEligibleCount(2);
        backtest.setPredictedCount(1); backtest.setCorrectCount(1); backtest.setSkippedCount(1); backtest.setMetrics("{}");
        backtest.setEngineVersion("msa-core-v1"); backtest.setRuntime("{}");
        for (boolean skipped : List.of(false, true)) {
            LocalDate target = LocalDate.parse(skipped ? "2025-02-21" : "2025-02-20");
            var prediction = new BacktestPredictionEntity(); prediction.setId(new BacktestPredictionId(null, target));
            prediction.setOriginDate(target.minusDays(1)); prediction.setTrainStart(condition.getStartDate()); prediction.setTrainEnd(target.minusDays(1));
            prediction.setActualState("UP"); prediction.setStatus(skipped ? "SKIPPED" : "SCORED");
            if (skipped) { prediction.setSkipCode("ZERO_ROW_UNESTIMATED"); }
            else { prediction.setPredictedState("UP"); prediction.setMajorityState("UP"); prediction.setPersistenceState("FLAT"); prediction.setProbabilities("[0.9,0.05,0.05]"); }
            backtest.addPrediction(prediction);
        }
        backtests.saveAndFlush(backtest); entityManager.flush();
        return List.of(condition.getId(), dataset.getId(), analysis.getId(), backtest.getId());
    }

    private void fixture() {
        datasetId = dataset(); conditionId = condition(); analysisId = analysis(conditionId, datasetId); backtestId = backtest();
        jdbc.update("INSERT INTO transition_probabilities(analysis_id,from_state,to_state,transition_count,probability) VALUES(?,'UP','UP',30,1)", analysisId);
        jdbc.update(predictionSql(), backtestId);
    }
    private long dataset() {
        return insert("INSERT INTO price_datasets(stock_id,provider,provider_version,price_basis,adjustment_policy,fetched_at,"
                + "coverage_start,coverage_end,row_count,content_sha256,metadata)"
                + " VALUES(7203,'FIXTURE','1','PROVIDER_ADJUSTED_CLOSE','PROVIDER_ADJUSTED_CLOSE_V1','2026-10-06 01:00:00',"
                + "'2025-01-06','2025-02-28',2,?,'{}')", "a".repeat(64));
    }
    private long condition() {
        return insert("INSERT INTO analysis_conditions(name,stock_id,start_date,end_date,lower_threshold,upper_threshold,estimator,window_mode,horizons)"
                + " VALUES('DB fixture',7203,'2025-01-06','2025-02-28',-0.005,0.005,'MLE_STRICT','FULL','[1,3,5,10]')");
    }
    private long analysis(long condition, long dataset) {
        return insert("INSERT INTO analysis_results(condition_id,dataset_id,as_of_date,current_state,sample_count,transition_count,"
                + "prediction_status,forecasts,quality,engine_version,runtime,result_sha256)"
                + " VALUES(?,?,'2025-02-19','UP',31,30,'AVAILABLE','[]','{}','msa-core-v1','{}',?)", condition, dataset, "b".repeat(64));
    }
    private long backtest() {
        return insert("INSERT INTO backtest_results(condition_id,dataset_id,test_start,test_end,min_train_states,training_mode,"
                + "eligible_count,predicted_count,correct_count,skipped_count,metrics,engine_version,runtime)"
                + " VALUES(?,?,'2025-02-20','2025-02-21',30,'EXPANDING',1,1,1,0,'{}','msa-core-v1','{}')", conditionId, datasetId);
    }
    private String predictionSql() {
        return "INSERT INTO backtest_predictions(backtest_id,target_date,origin_date,train_start,train_end,actual_state,predicted_state,"
                + "probabilities,majority_state,persistence_state,status)"
                + " VALUES(?,'2025-02-20','2025-02-19','2025-01-06','2025-02-19','UP','UP','[0.9,0.05,0.05]','UP','FLAT','SCORED')";
    }
    private long insert(String sql, Object... values) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) { statement.setObject(i + 1, values[i]); }
            return statement;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
    private void violation(int expectedCode, String sql, Object... values) {
        DataAccessException error = assertThrows(DataAccessException.class, () -> jdbc.update(sql, values));
        assertInstanceOf(SQLException.class, error.getMostSpecificCause());
        assertEquals(expectedCode, ((SQLException) error.getMostSpecificCause()).getErrorCode(), error.getMessage());
    }
}
