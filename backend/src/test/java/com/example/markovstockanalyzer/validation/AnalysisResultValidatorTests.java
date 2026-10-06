package com.example.markovstockanalyzer.validation;

import com.example.markovstockanalyzer.dto.response.AnalysisWarning;
import com.example.markovstockanalyzer.dto.response.CalculatedAnalysis;
import com.example.markovstockanalyzer.dto.response.ForecastPayload;
import com.example.markovstockanalyzer.exception.CalculationInvariantFailedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnalysisResultValidatorTests {
    private final AnalysisResultValidator validator = new AnalysisResultValidator();

    @Test
    void acceptsValidAvailableAnalysis() {
        assertDoesNotThrow(() -> validator.validate(new Fixture().result()));
    }

    @Test
    void acceptsUnestimatedNullRowWithUnavailableForecastsAndWarning() {
        assertDoesNotThrow(() -> validator.validate(unavailable().result()));
    }

    @Test
    void acceptsProbabilitySumsWithinApiTolerance() {
        Fixture fixture = new Fixture();
        fixture.matrix.set(0, List.of(0.5, 0.25, 0.25 + 5e-10));
        fixture.forecasts.set(0, new ForecastPayload(1, List.of(0.5, 0.25, 0.25 - 5e-10)));

        assertDoesNotThrow(() -> validator.validate(fixture.result()));
    }

    @Test
    void validatesContractWithoutRecomputingMleOrForecasts() {
        Fixture fixture = new Fixture();
        fixture.matrix = new ArrayList<>(List.of(
                List.of(1.0, 0.0, 0.0), List.of(0.0, 1.0, 0.0), List.of(0.0, 0.0, 1.0)));
        fixture.forecasts.replaceAll(forecast -> new ForecastPayload(forecast.horizon(), List.of(0.0, 0.0, 1.0)));

        assertDoesNotThrow(() -> validator.validate(fixture.result()));
    }

    @Test
    void comparesEngineVersionWithExplicitExpectedVersion() {
        Fixture fixture = new Fixture();
        fixture.engineVersion = "msa-core-v2";

        assertDoesNotThrow(() -> validator.validate(fixture.result(), "msa-core-v2"));
        assertThrows(CalculationInvariantFailedException.class, () -> validator.validate(fixture.result(), "msa-core-v1"));
        assertThrows(CalculationInvariantFailedException.class, () -> validator.validate(fixture.result(), null));
    }

    @Test
    void rejectsNullResponseWithDedicatedErrorCode() {
        CalculationInvariantFailedException error = assertThrows(
                CalculationInvariantFailedException.class, () -> validator.validate(null));

        assertEquals("CALCULATION_INVARIANT_FAILED", error.getCode());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidResults")
    void rejectsBrokenResultContract(String description, Consumer<Fixture> change) {
        Fixture fixture = new Fixture();
        change.accept(fixture);

        CalculationInvariantFailedException error = assertThrows(
                CalculationInvariantFailedException.class, () -> validator.validate(fixture.result()));

        assertEquals("CALCULATION_INVARIANT_FAILED", error.getCode());
    }

    private static Stream<Arguments> invalidResults() {
        return Stream.of(
                invalid("reordered states", f -> f.stateOrder = List.of("FLAT", "UP", "DOWN")),
                invalid("missing state", f -> f.stateOrder = List.of("UP", "FLAT")),
                invalid("null stateOrder", f -> f.stateOrder = null),
                invalid("invalid currentState", f -> f.currentState = "OTHER"),
                invalid("null currentState", f -> f.currentState = null),
                invalid("wrong engineVersion", f -> f.engineVersion = "msa-core-v2"),
                invalid("null engineVersion", f -> f.engineVersion = null),
                invalid("null sampleCount", f -> f.sampleCount = null),
                invalid("insufficient sampleCount", f -> { f.sampleCount = 29; f.transitionCount = 28; }),
                invalid("null transitionCount", f -> f.transitionCount = null),
                invalid("negative transitionCount", f -> f.transitionCount = -1),
                invalid("sample/transition mismatch", f -> f.transitionCount = 29),
                invalid("count total mismatch", f -> f.counts.set(0, List.of(8, 4, 4))),
                invalid("count total must not overflow int", f -> f.counts = new ArrayList<>(List.of(
                        List.of(Integer.MAX_VALUE, 1, 1), List.of(Integer.MAX_VALUE, 1, 1), List.of(26, 1, 1)))),
                invalid("negative count", f -> f.counts.set(0, List.of(-1, 4, 4))),
                invalid("null count", f -> f.counts.set(0, Arrays.asList(null, 4, 4))),
                invalid("null counts matrix", f -> f.counts = null),
                invalid("counts matrix row count", f -> f.counts.removeLast()),
                invalid("counts matrix column count", f -> f.counts.set(0, List.of(7, 4))),
                invalid("null counts row", f -> f.counts.set(0, null)),
                invalid("null probability matrix", f -> f.matrix = null),
                invalid("probability matrix row count", f -> f.matrix.removeLast()),
                invalid("probability matrix column count", f -> f.matrix.set(0, List.of(0.5, 0.5))),
                invalid("null probability row", f -> f.matrix.set(0, null)),
                invalid("null probabilities with positive counts", f -> f.matrix.set(0, nullRow())),
                invalid("estimated probabilities with zero counts", f -> {
                    f.counts.set(1, List.of(0, 0, 0)); f.counts.set(0, List.of(14, 4, 4));
                }),
                invalid("UNAVAILABLE with fully estimated matrix", f -> { f.status = "UNAVAILABLE"; f.forecasts.clear(); }),
                invalid("unknown predictionStatus", f -> f.status = "OTHER"),
                invalid("null predictionStatus", f -> f.status = null),
                invalid("null forecasts", f -> f.forecasts = null),
                invalid("missing forecast", f -> f.forecasts.removeLast()),
                invalid("extra forecast", f -> f.forecasts.add(new ForecastPayload(30, List.of(1.0, 0.0, 0.0)))),
                invalid("null forecast", f -> f.forecasts.set(0, null)),
                invalid("null horizon", f -> f.forecasts.set(0, new ForecastPayload(null, List.of(1.0, 0.0, 0.0)))),
                invalid("duplicate horizon", f -> f.forecasts.set(1, new ForecastPayload(1, List.of(1.0, 0.0, 0.0)))),
                invalid("wrong horizon order", f -> java.util.Collections.swap(f.forecasts, 0, 1)),
                invalid("null asOfDate", f -> f.asOfDate = null),
                invalid("null warnings", f -> f.warnings = null),
                invalid("null runtime", f -> f.runtime = null)
        );
    }

    @ParameterizedTest(name = "matrix and forecast: {0}")
    @MethodSource("invalidDistributions")
    void rejectsInvalidMatrixAndForecastProbabilities(String description, List<Double> probabilities) {
        Fixture matrixFixture = new Fixture();
        matrixFixture.matrix.set(0, probabilities);
        assertThrows(CalculationInvariantFailedException.class, () -> validator.validate(matrixFixture.result()));

        Fixture forecastFixture = new Fixture();
        forecastFixture.forecasts.set(0, new ForecastPayload(1, probabilities));
        assertThrows(CalculationInvariantFailedException.class, () -> validator.validate(forecastFixture.result()));
    }

    private static Stream<Arguments> invalidDistributions() {
        return Stream.of(
                Arguments.of("null vector", null),
                Arguments.of("two elements", List.of(0.5, 0.5)),
                Arguments.of("partially null", Arrays.asList(0.5, null, 0.5)),
                Arguments.of("NaN", List.of(Double.NaN, 0.5, 0.5)),
                Arguments.of("positive infinity", List.of(Double.POSITIVE_INFINITY, 0.5, 0.5)),
                Arguments.of("negative infinity", List.of(Double.NEGATIVE_INFINITY, 0.5, 0.5)),
                Arguments.of("negative value with unit sum", List.of(-0.1, 0.5, 0.6)),
                Arguments.of("value over one with unit sum", List.of(1.1, 0.0, -0.1)),
                Arguments.of("wrong sum", List.of(0.2, 0.2, 0.2)),
                Arguments.of("outside tolerance", List.of(0.5, 0.25, 0.25 + 2e-9)),
                Arguments.of("zero row", List.of(0.0, 0.0, 0.0))
        );
    }

    @Test
    void rejectsAvailableWithUnestimatedRow() {
        Fixture fixture = unavailable();
        fixture.status = "AVAILABLE";

        assertThrows(CalculationInvariantFailedException.class, () -> validator.validate(fixture.result()));
    }

    @Test
    void rejectsUnavailableWithForecasts() {
        Fixture fixture = unavailable();
        fixture.forecasts.add(new ForecastPayload(1, List.of(1.0, 0.0, 0.0)));

        assertThrows(CalculationInvariantFailedException.class, () -> validator.validate(fixture.result()));
    }

    @Test
    void rejectsUnavailableWithoutRequiredWarning() {
        Fixture fixture = unavailable();
        fixture.warnings = List.of();

        assertThrows(CalculationInvariantFailedException.class, () -> validator.validate(fixture.result()));
    }

    private static Arguments invalid(String description, Consumer<Fixture> change) {
        return Arguments.of(description, change);
    }

    private static List<Double> nullRow() {
        return Arrays.asList(null, null, null);
    }

    private static Fixture unavailable() {
        Fixture fixture = new Fixture();
        fixture.counts.set(0, List.of(14, 4, 4));
        fixture.counts.set(1, List.of(0, 0, 0));
        fixture.matrix.set(1, nullRow());
        fixture.status = "UNAVAILABLE";
        fixture.forecasts.clear();
        fixture.warnings = List.of(new AnalysisWarning("ZERO_ROW_UNESTIMATED", List.of("FLAT")));
        return fixture;
    }

    private static class Fixture {
        List<String> stateOrder = List.of("UP", "FLAT", "DOWN");
        LocalDate asOfDate = LocalDate.parse("2025-02-19");
        String currentState = "FLAT";
        Integer sampleCount = 31;
        Integer transitionCount = 30;
        List<List<Integer>> counts = new ArrayList<>(List.of(List.of(7, 4, 4), List.of(3, 0, 4), List.of(4, 4, 0)));
        List<List<Double>> matrix = new ArrayList<>(List.of(
                List.of(7.0 / 15, 4.0 / 15, 4.0 / 15), List.of(3.0 / 7, 0.0, 4.0 / 7), List.of(0.5, 0.5, 0.0)));
        String status = "AVAILABLE";
        List<ForecastPayload> forecasts = new ArrayList<>(List.of(
                new ForecastPayload(1, List.of(0.4, 0.2, 0.4)), new ForecastPayload(3, List.of(0.4, 0.2, 0.4)),
                new ForecastPayload(5, List.of(0.4, 0.2, 0.4)), new ForecastPayload(10, List.of(0.4, 0.2, 0.4))));
        List<AnalysisWarning> warnings = List.of();
        String engineVersion = "msa-core-v1";
        Map<String, Object> runtime = Map.of();

        CalculatedAnalysis result() {
            return new CalculatedAnalysis(stateOrder, asOfDate, currentState, sampleCount, transitionCount,
                    counts, matrix, status, forecasts, warnings, engineVersion, runtime);
        }
    }
}
