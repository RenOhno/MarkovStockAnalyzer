package com.example.markovstockanalyzer.persistence;

import com.example.markovstockanalyzer.persistence.entity.*;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class PersistenceMappingTests {
    private static final List<Class<?>> ENTITIES = List.of(StockEntity.class, PriceDatasetEntity.class, StockPriceEntity.class,
            AnalysisConditionEntity.class, AnalysisResultEntity.class, TransitionProbabilityEntity.class,
            BacktestResultEntity.class, BacktestPredictionEntity.class);

    @Test void allEightMappingsBootWithoutDatabaseAndCoverEveryMigrationColumn() throws Exception {
        try (var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.hbm2ddl.auto", "none").build()) {
            var sources = new MetadataSources(registry);
            ENTITIES.forEach(sources::addAnnotatedClass);
            var metadata = sources.buildMetadata();
            assertEquals(8, metadata.getEntityBindings().size());
            String ddl = new ClassPathResource("db/migration/V1__initial_schema.sql")
                    .getContentAsString(StandardCharsets.UTF_8);
            var tables = Pattern.compile("CREATE TABLE (\\w+) \\(\\n(.*?)\\n\\) ENGINE", Pattern.DOTALL).matcher(ddl);
            Map<String, Set<String>> columns = new HashMap<>();
            while (tables.find()) {
                Set<String> names = new HashSet<>();
                var fields = Pattern.compile("(?m)^  ([a-z0-9_]+) (?:BIGINT|INT|TINYINT|SMALLINT|DECIMAL|VARCHAR|CHAR|DATETIME|DATE|BOOLEAN|DOUBLE|JSON)").matcher(tables.group(2));
                while (fields.find()) { names.add(fields.group(1)); }
                columns.put(tables.group(1), names);
            }
            assertEquals(8, columns.size());
            for (var entity : metadata.getEntityBindings()) {
                Set<String> mapped = new HashSet<>();
                entity.getTable().getColumns().forEach(column -> mapped.add(column.getName()));
                assertEquals(columns.get(entity.getTable().getName()), mapped, entity.getEntityName());
            }
            try (var sessionFactory = metadata.buildSessionFactory()) {
                assertEquals(8, sessionFactory.getMetamodel().getEntities().size());
            }
        }
    }

    @Test void compositeKeysHaveValueEqualityAndAllPartsDistinguishRows() {
        var date = LocalDate.parse("2025-01-06");
        assertEquals(new StockPriceId(1L, date), new StockPriceId(1L, date));
        assertEquals(new StockPriceId(1L, date).hashCode(), new StockPriceId(1L, date).hashCode());
        assertNotEquals(new StockPriceId(1L, date), new StockPriceId(2L, date));
        assertNotEquals(new StockPriceId(1L, date), new StockPriceId(1L, date.plusDays(1)));
        assertEquals(new TransitionProbabilityId(1L, "UP", "DOWN"), new TransitionProbabilityId(1L, "UP", "DOWN"));
        assertNotEquals(new TransitionProbabilityId(1L, "UP", "DOWN"), new TransitionProbabilityId(1L, "UP", "FLAT"));
        assertEquals(new BacktestPredictionId(1L, date), new BacktestPredictionId(1L, date));
        assertNotEquals(new BacktestPredictionId(1L, date), new BacktestPredictionId(2L, date));
    }

    @Test void aggregateHelpersSetOwningReferencesForTransactionalCascade() {
        var dataset = new PriceDatasetEntity(); var price = new StockPriceEntity(); dataset.addPrice(price);
        assertSame(dataset, price.getDataset()); assertEquals(List.of(price), dataset.getPrices());
        var analysis = new AnalysisResultEntity(); var transition = new TransitionProbabilityEntity(); analysis.addTransition(transition);
        assertSame(analysis, transition.getAnalysis()); assertEquals(List.of(transition), analysis.getTransitions());
        var backtest = new BacktestResultEntity(); var prediction = new BacktestPredictionEntity(); backtest.addPrediction(prediction);
        assertSame(backtest, prediction.getBacktest()); assertEquals(List.of(prediction), backtest.getPredictions());
    }
}
