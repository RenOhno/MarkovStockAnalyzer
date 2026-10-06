package com.example.markovstockanalyzer.repository;

import com.example.markovstockanalyzer.dto.response.AnalysisWarning;
import com.example.markovstockanalyzer.dto.response.CalculatedAnalysis;
import com.example.markovstockanalyzer.dto.response.ForecastPayload;
import com.example.markovstockanalyzer.model.AnalysisResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryAnalysisResultRepositoryTests {
    private final AnalysisResultRepository repository = new InMemoryAnalysisResultRepository();

    @Test
    void assignsIdAndRetrievesResultWithConditionAndDatasetReferences() {
        CalculatedAnalysis calculated = new Fixture().result();

        AnalysisResult saved = repository.save(101L, 501L, calculated);

        assertTrue(saved.id() > 0);
        assertEquals(101L, saved.conditionId());
        assertEquals(501L, saved.datasetId());
        assertEquals(calculated, saved.calculatedAnalysis());
        assertNotNull(saved.createdAt());
        assertEquals(saved, repository.findById(saved.id()).orElseThrow());
    }

    @Test
    void eachSaveKeepsItsOwnIdAndReferences() {
        AnalysisResult first = repository.save(101L, 501L, new Fixture().result());
        AnalysisResult second = repository.save(102L, 502L, new Fixture().result());

        assertNotEquals(first.id(), second.id());
        assertEquals(first, repository.findById(first.id()).orElseThrow());
        assertEquals(second, repository.findById(second.id()).orElseThrow());
        assertEquals(101L, first.conditionId());
        assertEquals(501L, first.datasetId());
        assertEquals(102L, second.conditionId());
        assertEquals(502L, second.datasetId());
    }

    @Test
    void returnsEmptyForUnknownId() {
        assertTrue(repository.findById(999L).isEmpty());
    }

    @Test
    void storesUnavailableResultsWithNullProbabilityCells() {
        Fixture fixture = new Fixture();
        fixture.counts = new ArrayList<>(List.of(List.of(15, 0, 0), List.of(0, 0, 0), List.of(0, 0, 15)));
        fixture.matrix = new ArrayList<>(List.of(
                List.of(1.0, 0.0, 0.0), Arrays.asList(null, null, null), List.of(0.0, 0.0, 1.0)));
        fixture.status = "UNAVAILABLE";
        fixture.forecasts.clear();
        fixture.warnings = new ArrayList<>(List.of(new AnalysisWarning("ZERO_ROW_UNESTIMATED", List.of("FLAT"))));
        CalculatedAnalysis calculated = fixture.result();

        AnalysisResult saved = repository.save(101L, 501L, calculated);

        assertEquals(calculated, saved.calculatedAnalysis());
        assertEquals(Arrays.asList(null, null, null), saved.calculatedAnalysis().transitionMatrix().get(1));
        assertEquals(List.of(), saved.calculatedAnalysis().forecasts());
        assertThrows(UnsupportedOperationException.class, () -> saved.calculatedAnalysis().transitionMatrix().get(1).clear());
    }

    @Test
    void protectsSavedMatrixForecastsWarningsAndRuntimeFromMutation() {
        Fixture fixture = new Fixture();
        AnalysisResult saved = repository.save(101L, 501L, fixture.result());

        fixture.stateOrder.clear();
        fixture.counts.getFirst().set(0, 999);
        fixture.counts.clear();
        fixture.matrix.getFirst().set(0, 0.0);
        fixture.matrix.clear();
        fixture.forecasts.getFirst().probabilities().clear();
        fixture.forecasts.clear();
        fixture.warnings.getFirst().states().clear();
        fixture.warnings.clear();
        fixture.versions.clear();
        fixture.tags.clear();
        fixture.runtime.clear();

        CalculatedAnalysis stored = repository.findById(saved.id()).orElseThrow().calculatedAnalysis();
        assertEquals(new Fixture().result(), stored);
        assertThrows(UnsupportedOperationException.class, () -> stored.stateOrder().clear());
        assertThrows(UnsupportedOperationException.class, () -> stored.transitionCounts().clear());
        assertThrows(UnsupportedOperationException.class, () -> stored.transitionCounts().getFirst().clear());
        assertThrows(UnsupportedOperationException.class, () -> stored.transitionMatrix().getFirst().clear());
        assertThrows(UnsupportedOperationException.class, () -> stored.forecasts().getFirst().probabilities().clear());
        assertThrows(UnsupportedOperationException.class, () -> stored.warnings().getFirst().states().clear());
        assertThrows(UnsupportedOperationException.class, () -> stored.runtime().clear());
        assertThrows(UnsupportedOperationException.class, () -> ((Map<?, ?>) stored.runtime().get("dependencyVersions")).clear());
        assertThrows(UnsupportedOperationException.class, () -> ((List<?>) stored.runtime().get("tags")).clear());
    }

    private static class Fixture {
        List<String> stateOrder = new ArrayList<>(List.of("UP", "FLAT", "DOWN"));
        List<List<Integer>> counts = new ArrayList<>(List.of(
                new ArrayList<>(List.of(10, 0, 0)), new ArrayList<>(List.of(0, 10, 0)), new ArrayList<>(List.of(0, 0, 10))));
        List<List<Double>> matrix = new ArrayList<>(List.of(
                new ArrayList<>(List.of(1.0, 0.0, 0.0)), new ArrayList<>(List.of(0.0, 1.0, 0.0)),
                new ArrayList<>(List.of(0.0, 0.0, 1.0))));
        String status = "AVAILABLE";
        List<ForecastPayload> forecasts = new ArrayList<>(List.of(1, 3, 5, 10).stream()
                .map(horizon -> new ForecastPayload(horizon, new ArrayList<>(List.of(1.0, 0.0, 0.0)))).toList());
        List<AnalysisWarning> warnings = new ArrayList<>(List.of(
                new AnalysisWarning("LOW_ROW_SUPPORT", new ArrayList<>(List.of("UP", "FLAT", "DOWN")))));
        Map<String, Object> versions = new LinkedHashMap<>(Map.of("numpy", "2.2.0"));
        List<String> tags = new ArrayList<>(List.of("SYNTHETIC_FIXTURE"));
        Map<String, Object> runtime = new LinkedHashMap<>(Map.of("dependencyVersions", versions, "tags", tags));

        CalculatedAnalysis result() {
            return new CalculatedAnalysis(stateOrder, LocalDate.parse("2025-02-19"), "UP", 31, 30,
                    counts, matrix, status, forecasts, warnings, "msa-core-v1", runtime);
        }
    }
}
