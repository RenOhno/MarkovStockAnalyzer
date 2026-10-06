package com.example.markovstockanalyzer.repository;

import com.example.markovstockanalyzer.dto.response.*;
import com.example.markovstockanalyzer.support.BacktestFixtures;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryBacktestResultRepositoryTests {
    private final BacktestResultRepository repository = new InMemoryBacktestResultRepository();
    @Test void assignsDistinctIdsAndStoresAllInputReferencesAndEvaluationValues() {
        var first = repository.save(101L, 501L, BacktestFixtures.REQUEST.evaluation(), BacktestFixtures.mixed());
        var second = repository.save(102L, 502L, BacktestFixtures.REQUEST.evaluation(), BacktestFixtures.mixed());
        assertTrue(first.id() > 0); assertNotEquals(first.id(), second.id());
        assertEquals(101L, first.conditionId()); assertEquals(501L, first.datasetId());
        assertEquals(BacktestFixtures.REQUEST.testStart(), first.testStart());
        assertEquals(BacktestFixtures.REQUEST.testEnd(), first.testEnd());
        assertEquals(1, first.horizon()); assertEquals(30, first.minTrainStates());
        assertEquals("EXPANDING", first.trainingMode()); assertNull(first.windowSize()); assertNotNull(first.createdAt());
        assertEquals(first, repository.findById(first.id()).orElseThrow());
        assertEquals(second, repository.findById(second.id()).orElseThrow());
        assertEquals(BacktestFixtures.mixed(), first.calculatedBacktest());
    }
    @Test void keepsNullableMetricsAndSkippedProbabilities() {
        var saved = repository.save(101L, 501L, BacktestFixtures.REQUEST.evaluation(), BacktestFixtures.allSkipped());
        assertEquals(BacktestFixtures.allSkipped(), saved.calculatedBacktest());
        assertNull(saved.calculatedBacktest().predictions().getFirst().probabilities());
        assertNull(saved.calculatedBacktest().summary().metrics().precision().getFirst());
    }
    @Test void savedNestedValuesCannotBeChangedByCallerOrReader() {
        var base = BacktestFixtures.mixed();
        List<Double> precision = new ArrayList<>(base.summary().metrics().precision());
        List<List<Integer>> matrix = new ArrayList<>(base.summary().metrics().confusionMatrix().stream().map(ArrayList::new).toList());
        Map<String, Integer> reasons = new HashMap<>(base.summary().metrics().skipReasons());
        var m = base.summary().metrics();
        var metrics = new BacktestMetrics(m.accuracy(), precision, m.recall(), matrix, m.brierScore(), m.logLoss(),
                m.majorityAccuracy(), m.persistenceAccuracy(), m.tieCount(), reasons);
        var s = base.summary();
        var summary = new BacktestSummary(s.testStart(), s.testEnd(), s.horizon(), s.eligibleCount(), s.predictedCount(),
                s.correctCount(), s.skippedCount(), s.coverage(), metrics);
        List<Double> probabilities = new ArrayList<>(BacktestFixtures.scored().probabilities());
        var p = BacktestFixtures.scored();
        List<BacktestPrediction> predictions = new ArrayList<>(List.of(new BacktestPrediction(p.originDate(), p.targetDate(), p.trainStart(),
                p.trainEnd(), p.actualState(), p.predictedState(), probabilities, p.majorityState(), p.persistenceState(), p.status(), p.skipCode()),
                BacktestFixtures.skipped()));
        Map<String, Object> versions = new HashMap<>(Map.of("numpy", "2.2.0"));
        Map<String, Object> runtime = new HashMap<>(Map.of("dependencyVersions", versions));
        var saved = repository.save(101L, 501L, BacktestFixtures.REQUEST.evaluation(),
                new CalculatedBacktest(summary, predictions, "msa-core-v1", runtime));
        precision.clear(); matrix.getFirst().clear(); matrix.clear(); reasons.clear();
        probabilities.clear(); predictions.clear(); versions.clear(); runtime.clear();
        var stored = repository.findById(saved.id()).orElseThrow().calculatedBacktest();
        assertEquals(Arrays.asList(1.0, null, null), stored.summary().metrics().precision());
        assertEquals(List.of(1, 0, 0), stored.summary().metrics().confusionMatrix().getFirst());
        assertEquals(Map.of("ZERO_ROW_UNESTIMATED", 1), stored.summary().metrics().skipReasons());
        assertEquals(List.of(0.9, 0.05, 0.05), stored.predictions().getFirst().probabilities());
        assertEquals(Map.of("numpy", "2.2.0"), stored.runtime().get("dependencyVersions"));
        assertThrows(UnsupportedOperationException.class, () -> stored.predictions().clear());
        assertThrows(UnsupportedOperationException.class, () -> stored.predictions().getFirst().probabilities().clear());
        assertThrows(UnsupportedOperationException.class, () -> stored.summary().metrics().precision().clear());
        assertThrows(UnsupportedOperationException.class, () -> stored.summary().metrics().confusionMatrix().getFirst().clear());
        assertThrows(UnsupportedOperationException.class, () -> ((Map<?, ?>) stored.runtime().get("dependencyVersions")).clear());
    }
    @Test void unknownIdReturnsEmpty() { assertTrue(repository.findById(999L).isEmpty()); }
}
