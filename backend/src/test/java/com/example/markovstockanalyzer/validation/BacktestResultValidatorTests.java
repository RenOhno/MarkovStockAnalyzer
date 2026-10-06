package com.example.markovstockanalyzer.validation;

import com.example.markovstockanalyzer.dto.response.*;
import com.example.markovstockanalyzer.exception.CalculationInvariantFailedException;
import com.example.markovstockanalyzer.support.BacktestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class BacktestResultValidatorTests {
    private final BacktestResultValidator validator = new BacktestResultValidator();

    @Test void acceptsScoredAndSkipped() { assertDoesNotThrow(() -> validator.validate(BacktestFixtures.mixed(), BacktestFixtures.input())); }
    @Test void acceptsAllSkippedNullableMetrics() { assertDoesNotThrow(() -> validator.validate(BacktestFixtures.allSkipped(), BacktestFixtures.input())); }
    @Test void acceptsSmallProbabilityAndCoverageRoundoff() {
        Fixture f = new Fixture();
        f.coverage = 0.5 + 5e-10;
        f.predictions.set(0, prediction("UP", List.of(0.9, 0.05, 0.05 + 5e-10), "UP", "FLAT", "SCORED", null));
        assertDoesNotThrow(() -> validator.validate(f.result(), BacktestFixtures.input()));
    }
    @Test void rejectsNullResultWithUnifiedCode() {
        var error = assertThrows(CalculationInvariantFailedException.class, () -> validator.validate(null, BacktestFixtures.input()));
        assertEquals("CALCULATION_INVARIANT_FAILED", error.getCode());
    }
    @ParameterizedTest(name = "{0}") @MethodSource("invalidResults")
    void rejectsMainInvariantFailures(String name, Consumer<Fixture> change) {
        Fixture f = new Fixture(); change.accept(f);
        var error = assertThrows(CalculationInvariantFailedException.class, () -> validator.validate(f.result(), BacktestFixtures.input()));
        assertEquals("CALCULATION_INVARIANT_FAILED", error.getCode());
    }
    static Stream<Arguments> invalidResults() {
        return Stream.of(
                bad("eligible zero", f -> f.eligible = 0), bad("eligible over cap", f -> f.eligible = 1001),
                bad("negative predicted", f -> f.predicted = -1), bad("negative skipped", f -> f.skipped = -1),
                bad("negative correct", f -> f.correct = -1), bad("counts mismatch", f -> f.predicted = 2),
                bad("correct exceeds predicted", f -> f.correct = 2),
                bad("prediction length", f -> f.predictions.removeLast()), bad("null predictions", f -> f.predictions = null),
                bad("wrong coverage", f -> f.coverage = 0.8), bad("NaN coverage", f -> f.coverage = Double.NaN),
                bad("null metrics", f -> f.noMetrics = true),
                bad("matrix rows", f -> f.matrix.removeLast()), bad("matrix columns", f -> f.matrix.set(0, List.of(1, 0))),
                bad("matrix negative", f -> f.matrix.set(0, List.of(1, -1, 1))),
                bad("matrix total", f -> f.matrix.set(0, List.of(1, 1, 0))),
                bad("matrix diagonal", f -> f.matrix.set(0, List.of(0, 1, 0))),
                bad("precision dimension", f -> f.precision = List.of(1.0)),
                bad("recall dimension", f -> f.recall = List.of(1.0)),
                bad("precision NaN", f -> f.precision = Arrays.asList(Double.NaN, null, null)),
                bad("recall infinity", f -> f.recall = Arrays.asList(Double.POSITIVE_INFINITY, null, null)),
                bad("accuracy wrong", f -> f.accuracy = 0.5),
                bad("baseline negative", f -> f.majority = -0.1), bad("baseline nonfinite", f -> f.persistence = Double.NaN),
                bad("negative brier", f -> f.brier = -1.0), bad("infinite loss", f -> f.loss = Double.POSITIVE_INFINITY),
                bad("invalid tie count", f -> f.ties = 2), bad("skip total", f -> f.reasons = Map.of("ZERO_ROW_UNESTIMATED", 2)),
                bad("skip reason mismatch", f -> f.reasons = Map.of("OTHER_REASON", 1)),
                bad("missing predicted state", f -> f.predictions.set(0, prediction(null, List.of(1.0, 0.0, 0.0), "UP", "FLAT", "SCORED", null))),
                bad("missing probabilities", f -> f.predictions.set(0, prediction("UP", null, "UP", "FLAT", "SCORED", null))),
                bad("probability dimension", f -> f.predictions.set(0, prediction("UP", List.of(1.0, 0.0), "UP", "FLAT", "SCORED", null))),
                bad("probability sum", f -> f.predictions.set(0, prediction("UP", List.of(0.4, 0.1, 0.1), "UP", "FLAT", "SCORED", null))),
                bad("probability NaN", f -> f.predictions.set(0, prediction("UP", List.of(Double.NaN, 0.0, 0.0), "UP", "FLAT", "SCORED", null))),
                bad("negative probability", f -> f.predictions.set(0, prediction("UP", List.of(-0.1, 0.5, 0.6), "UP", "FLAT", "SCORED", null))),
                bad("missing majority", f -> f.predictions.set(0, prediction("UP", List.of(1.0, 0.0, 0.0), null, "FLAT", "SCORED", null))),
                bad("missing persistence", f -> f.predictions.set(0, prediction("UP", List.of(1.0, 0.0, 0.0), "UP", null, "SCORED", null))),
                bad("scored has skip code", f -> f.predictions.set(0, prediction("UP", List.of(1.0, 0.0, 0.0), "UP", "FLAT", "SCORED", "OTHER"))),
                bad("unknown status", f -> f.predictions.set(0, prediction("UP", List.of(1.0, 0.0, 0.0), "UP", "FLAT", "OTHER", null))),
                bad("skipped predicted state", f -> f.predictions.set(1, skipped("UP", null, null, null, "ZERO_ROW_UNESTIMATED"))),
                bad("skipped probabilities", f -> f.predictions.set(1, skipped(null, List.of(1.0, 0.0, 0.0), null, null, "ZERO_ROW_UNESTIMATED"))),
                bad("skipped majority", f -> f.predictions.set(1, skipped(null, null, "UP", null, "ZERO_ROW_UNESTIMATED"))),
                bad("skipped persistence", f -> f.predictions.set(1, skipped(null, null, null, "UP", "ZERO_ROW_UNESTIMATED"))),
                bad("skipped missing code", f -> f.predictions.set(1, skipped(null, null, null, null, null))),
                bad("train dates reversed", f -> f.predictions.set(0, dates("2025-02-20", "2025-02-19", "2025-02-19", "2025-02-20"))),
                bad("train end differs", f -> f.predictions.set(0, dates("2025-01-06", "2025-02-18", "2025-02-19", "2025-02-20"))),
                bad("origin equals target", f -> f.predictions.set(0, dates("2025-01-06", "2025-02-20", "2025-02-20", "2025-02-20"))),
                bad("target outside range", f -> f.predictions.set(0, dates("2025-01-06", "2025-02-19", "2025-02-19", "2025-02-24"))),
                bad("training before condition", f -> f.predictions.set(0, dates("2024-12-30", "2025-02-19", "2025-02-19", "2025-02-20"))),
                bad("wrong summary period", f -> f.testStart = f.testStart.minusDays(1)),
                bad("wrong horizon", f -> f.horizon = 3), bad("wrong engine", f -> f.engine = "msa-core-v2"),
                bad("null runtime", f -> f.runtime = null));
    }
    static Arguments bad(String name, Consumer<Fixture> change) { return Arguments.of(name, change); }
    static BacktestPrediction prediction(String predicted, List<Double> probabilities, String majority, String persistence, String status, String code) {
        var p = BacktestFixtures.scored();
        return new BacktestPrediction(p.originDate(), p.targetDate(), p.trainStart(), p.trainEnd(), p.actualState(),
                predicted, probabilities, majority, persistence, status, code);
    }
    static BacktestPrediction skipped(String predicted, List<Double> probabilities, String majority, String persistence, String code) {
        var p = BacktestFixtures.skipped();
        return new BacktestPrediction(p.originDate(), p.targetDate(), p.trainStart(), p.trainEnd(), p.actualState(),
                predicted, probabilities, majority, persistence, "SKIPPED", code);
    }
    static BacktestPrediction dates(String start, String end, String origin, String target) {
        var p = BacktestFixtures.scored();
        return new BacktestPrediction(LocalDate.parse(origin), LocalDate.parse(target), LocalDate.parse(start), LocalDate.parse(end),
                p.actualState(), p.predictedState(), p.probabilities(), p.majorityState(), p.persistenceState(), p.status(), p.skipCode());
    }
    static class Fixture {
        LocalDate testStart = BacktestFixtures.REQUEST.testStart(), testEnd = BacktestFixtures.REQUEST.testEnd();
        Integer eligible = 2, predicted = 1, correct = 1, skipped = 1, horizon = 1, ties = 0;
        Double coverage = 0.5, accuracy = 1.0, brier = 0.015, loss = 0.10536051565782628, majority = 1.0, persistence = 0.0;
        List<Double> precision = Arrays.asList(1.0, null, null), recall = Arrays.asList(1.0, null, null);
        List<List<Integer>> matrix = new ArrayList<>(List.of(List.of(1, 0, 0), List.of(0, 0, 0), List.of(0, 0, 0)));
        Map<String, Integer> reasons = Map.of("ZERO_ROW_UNESTIMATED", 1);
        List<BacktestPrediction> predictions = new ArrayList<>(BacktestFixtures.mixed().predictions());
        String engine = "msa-core-v1";
        Map<String, Object> runtime = BacktestFixtures.runtime();
        boolean noMetrics;
        CalculatedBacktest result() {
            var metrics = noMetrics ? null : new BacktestMetrics(accuracy, precision, recall, matrix, brier, loss, majority, persistence, ties, reasons);
            return new CalculatedBacktest(new BacktestSummary(testStart, testEnd, horizon, eligible, predicted, correct, skipped, coverage, metrics),
                    predictions, engine, runtime);
        }
    }
}
