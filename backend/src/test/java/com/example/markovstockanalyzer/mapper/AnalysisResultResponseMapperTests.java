package com.example.markovstockanalyzer.mapper;

import com.example.markovstockanalyzer.dto.response.AnalysisResultResponse;
import com.example.markovstockanalyzer.dto.response.AnalysisWarning;
import com.example.markovstockanalyzer.dto.response.CalculatedAnalysis;
import com.example.markovstockanalyzer.dto.response.DataSourceResponse;
import com.example.markovstockanalyzer.dto.response.ForecastPayload;
import com.example.markovstockanalyzer.dto.response.PriceDatasetPayload;
import com.example.markovstockanalyzer.dto.response.PricePoint;
import com.example.markovstockanalyzer.dto.response.ProvenanceResponse;
import com.example.markovstockanalyzer.model.AnalysisResult;
import com.example.markovstockanalyzer.model.PriceDataset;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnalysisResultResponseMapperTests {
    private static final Instant CREATED_AT = Instant.parse("2026-10-06T02:00:00Z");
    private final AnalysisResultResponseMapper mapper = new AnalysisResultResponseMapper();
    private final JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void mapsSavedValuesAndBuildsDataSourceAndProvenanceFromTheirRespectiveSources() {
        Map<String, Object> runtime = new LinkedHashMap<>(Map.ofEntries(
                Map.entry("engineVersion", "msa-core-v1"), Map.entry("gitCommit", "a".repeat(40)),
                Map.entry("pythonVersion", "3.12.7"), Map.entry("numpyVersion", "2.2.0"),
                Map.entry("pandasVersion", "2.2.3"), Map.entry("normalizationVersion", "NORMALIZATION_V1"),
                Map.entry("configurationVersion", "analysis-config-v1"),
                Map.entry("provider", "DO_NOT_USE_RUNTIME_PROVIDER"),
                Map.entry("calendarName", "DO_NOT_USE_RUNTIME_CALENDAR"),
                Map.entry("createdAt", "1999-01-01T00:00:00Z")
        ));
        CalculatedAnalysis calculated = calculated(runtime, false);
        AnalysisResult saved = new AnalysisResult(1001L, 101L, 501L, calculated, CREATED_AT);

        AnalysisResultResponse response = mapper.map(saved, dataset(501L));

        assertEquals("1001", response.id());
        assertEquals("101", response.conditionId());
        assertEquals("501", response.datasetId());
        assertSame(calculated.stateOrder(), response.stateOrder());
        assertEquals("PROVIDER_ADJUSTED_CLOSE", response.priceBasis());
        assertEquals(calculated.asOfDate(), response.asOfDate());
        assertEquals(calculated.currentState(), response.currentState());
        assertEquals(calculated.sampleCount(), response.sampleCount());
        assertEquals(calculated.transitionCount(), response.transitionCount());
        assertSame(calculated.transitionCounts(), response.transitionCounts());
        assertSame(calculated.transitionMatrix(), response.transitionMatrix());
        assertEquals("AVAILABLE", response.predictionStatus());
        assertSame(calculated.forecasts(), response.forecasts());
        assertSame(calculated.warnings(), response.warnings());
        assertEquals(calculated.engineVersion(), response.engineVersion());
        assertEquals(CREATED_AT, response.createdAt());
        assertEquals(new DataSourceResponse(
                "YFINANCE", "0.2.65", "PROVIDER_ADJUSTED_CLOSE_V1", Instant.parse("2026-10-06T01:02:03Z"),
                LocalDate.parse("2024-12-30"), LocalDate.parse("2025-02-19"), "b".repeat(64), "XTKS", "4.11.1"
        ), response.dataSource());
        assertEquals(new ProvenanceResponse(
                "msa-core-v1", "a".repeat(40), Map.of("python", "3.12.7", "numpy", "2.2.0", "pandas", "2.2.3"),
                "NORMALIZATION_V1", "analysis-config-v1"
        ), response.provenance());
        assertEquals(CREATED_AT.toString(), jsonMapper.valueToTree(response).get("createdAt").asString());
    }

    @Test
    void exposesOnlyPermittedProvenanceAndDependencyKeys() {
        Map<String, Object> runtime = Map.ofEntries(
                Map.entry("engineVersion", "msa-core-v1"),
                Map.entry("dependencyVersions", Map.of(
                        "python", "3.12.7", "numpy", "2.2.0", "pandas", "2.2.3",
                        "INTERNAL_API_TOKEN", "nested-secret", "internalPath", "/private/analysis")),
                Map.entry("INTERNAL_API_TOKEN", "root-secret"), Map.entry("internalPath", "C:\\private\\analysis"),
                Map.entry("cookie", "session-secret"), Map.entry("environment", Map.of("password", "password-secret"))
        );

        AnalysisResultResponse response = map(runtime, false);
        String json = jsonMapper.writeValueAsString(response);
        JsonNode document = jsonMapper.readTree(json);

        assertEquals(Map.of("python", "3.12.7", "numpy", "2.2.0", "pandas", "2.2.3"),
                response.provenance().dependencyVersions());
        assertEquals(5, document.get("provenance").size());
        assertEquals(9, document.get("dataSource").size());
        for (String forbidden : List.of("INTERNAL_API_TOKEN", "root-secret", "nested-secret", "internalPath",
                "private", "session-secret", "password-secret", "metadata-secret", "runtime", "metadata")) {
            assertFalse(json.contains(forbidden), forbidden);
        }
        assertTrue(runtime.containsKey("INTERNAL_API_TOKEN"));
    }

    @Test
    void leavesMissingRuntimeValuesNullWithoutFallbackOrInventedValues() {
        for (Map<String, Object> runtime : Arrays.<Map<String, Object>>asList(Map.of(), null)) {
            AnalysisResultResponse response = map(runtime, false);

            assertEquals(new ProvenanceResponse(null, null, null, null, null), response.provenance());
            assertEquals("msa-core-v1", response.engineVersion());
            // Dataset metadata contains normalizationVersion, but provenance must come from runtime.
            assertNull(response.provenance().normalizationVersion());
        }
    }

    @Test
    void omitsPathsAndDiagnosticObjectsEvenUnderPermittedProvenanceKeys() {
        Map<String, Object> runtime = Map.of(
                "engineVersion", Map.of("token", "engine-secret"),
                "gitCommit", "/private/git", "normalizationVersion", "C:\\private\\normalization",
                "configurationVersion", "https://user:password-secret@example.com/config",
                "dependencyVersions", Map.of("python", Map.of("token", "python-secret"),
                        "numpy", "/private/numpy", "pandas", "2.2.3")
        );

        AnalysisResultResponse response = map(runtime, false);
        String json = jsonMapper.writeValueAsString(response);

        assertEquals(new ProvenanceResponse(null, null, Map.of("pandas", "2.2.3"), null, null), response.provenance());
        assertFalse(json.contains("private"));
        assertFalse(json.contains("secret"));
        assertFalse(json.contains("token"));
    }

    @Test
    void preservesUnavailableForecastsNullMatrixRowsAndWarnings() {
        CalculatedAnalysis calculated = calculated(Map.of(), true);
        AnalysisResult saved = new AnalysisResult(1001L, 101L, 501L, calculated, CREATED_AT);

        AnalysisResultResponse response = mapper.map(saved, dataset(501L));

        assertEquals("UNAVAILABLE", response.predictionStatus());
        assertSame(calculated.forecasts(), response.forecasts());
        assertEquals(List.of(), response.forecasts());
        assertSame(calculated.transitionCounts(), response.transitionCounts());
        assertSame(calculated.transitionMatrix(), response.transitionMatrix());
        assertEquals(Arrays.asList(null, null, null), response.transitionMatrix().get(1));
        assertSame(calculated.warnings(), response.warnings());
        assertTrue(jsonMapper.writeValueAsString(response).contains("[null,null,null]"));
    }

    @Test
    void serializesIdsAsStringsBeyondJavascriptIntegerPrecision() {
        long id = 9007199254740993L;
        AnalysisResult result = new AnalysisResult(id, id + 1, id + 2, calculated(Map.of(), false), CREATED_AT);

        AnalysisResultResponse response = mapper.map(result, dataset(id + 2));
        String json = jsonMapper.writeValueAsString(response);

        assertTrue(json.contains("\"id\":\"9007199254740993\""));
        assertTrue(json.contains("\"conditionId\":\"9007199254740994\""));
        assertTrue(json.contains("\"datasetId\":\"9007199254740995\""));
    }

    @Test
    void rejectsDifferentDatasetIdToAvoidWrongDataSource() {
        AnalysisResult result = new AnalysisResult(1001L, 101L, 501L, calculated(Map.of(), false), CREATED_AT);

        assertThrows(IllegalArgumentException.class, () -> mapper.map(result, dataset(502L)));
    }

    private AnalysisResultResponse map(Map<String, Object> runtime, boolean unavailable) {
        return mapper.map(new AnalysisResult(1001L, 101L, 501L, calculated(runtime, unavailable), CREATED_AT), dataset(501L));
    }

    private PriceDataset dataset(Long id) {
        PriceDatasetPayload payload = new PriceDatasetPayload(
                "7203.T", "XTKS", "Asia/Tokyo", "PROVIDER_ADJUSTED_CLOSE", "YFINANCE", "0.2.65",
                "PROVIDER_ADJUSTED_CLOSE_V1", OffsetDateTime.parse("2026-10-06T10:02:03+09:00"),
                LocalDate.parse("2024-12-30"), LocalDate.parse("2025-02-19"), "b".repeat(64),
                Map.of("calendarName", "XTKS", "calendarVersion", "4.11.1",
                        "normalizationVersion", "NORMALIZATION_FROM_DATASET", "INTERNAL_API_TOKEN", "metadata-secret"),
                List.of(new PricePoint(LocalDate.parse("2024-12-30"), "100.0000000000", "100.0000000000", null))
        );
        return new PriceDataset(id, "7203", payload, Instant.parse("2026-10-06T01:03:00Z"));
    }

    private CalculatedAnalysis calculated(Map<String, Object> runtime, boolean unavailable) {
        List<List<Integer>> counts = unavailable
                ? List.of(List.of(15, 0, 0), List.of(0, 0, 0), List.of(0, 0, 15))
                : List.of(List.of(7, 4, 4), List.of(3, 0, 4), List.of(4, 4, 0));
        List<List<Double>> matrix = unavailable
                ? List.of(List.of(1.0, 0.0, 0.0), Arrays.asList(null, null, null), List.of(0.0, 0.0, 1.0))
                : List.of(List.of(7.0 / 15, 4.0 / 15, 4.0 / 15), List.of(3.0 / 7, 0.0, 4.0 / 7), List.of(0.5, 0.5, 0.0));
        List<ForecastPayload> forecasts = unavailable ? List.of()
                : List.of(1, 3, 5, 10).stream().map(horizon -> new ForecastPayload(horizon, List.of(0.4, 0.2, 0.4))).toList();
        List<AnalysisWarning> warnings = unavailable
                ? List.of(new AnalysisWarning("ZERO_ROW_UNESTIMATED", List.of("FLAT"))) : List.of();
        return new CalculatedAnalysis(List.of("UP", "FLAT", "DOWN"), LocalDate.parse("2025-02-19"), "UP", 31, 30,
                counts, matrix, unavailable ? "UNAVAILABLE" : "AVAILABLE", forecasts, warnings, "msa-core-v1", runtime);
    }
}
