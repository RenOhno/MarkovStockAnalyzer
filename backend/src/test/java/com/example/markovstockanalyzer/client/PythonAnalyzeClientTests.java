package com.example.markovstockanalyzer.client;

import com.example.markovstockanalyzer.config.RestClientConfig;
import com.example.markovstockanalyzer.dto.request.AnalysisCondition;
import com.example.markovstockanalyzer.dto.request.AnalyzeInput;
import com.example.markovstockanalyzer.dto.response.AnalysisWarning;
import com.example.markovstockanalyzer.dto.response.CalculatedAnalysis;
import com.example.markovstockanalyzer.dto.response.ConditionResponse;
import com.example.markovstockanalyzer.dto.response.ForecastPayload;
import com.example.markovstockanalyzer.dto.response.PriceDatasetPayload;
import com.example.markovstockanalyzer.exception.AnalysisServiceUnavailableException;
import com.example.markovstockanalyzer.exception.PythonApiException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PythonAnalyzeClientTests {
    private static final String REQUEST_ID = "request-analyze-001";
    private static final String DATASET_JSON = """
            {
              "ticker": "7203.T", "exchange": "XTKS", "timeZone": "Asia/Tokyo",
              "priceBasis": "PROVIDER_ADJUSTED_CLOSE", "provider": "YFINANCE",
              "providerVersion": "0.2.65", "adjustmentPolicy": "PROVIDER_ADJUSTED_CLOSE_V1",
              "fetchedAt": "2026-10-06T01:02:03Z",
              "coverageStart": "2024-12-30", "coverageEnd": "2025-01-06",
              "contentSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
              "metadata": {
                "schemaVersion": 1, "normalizationVersion": "NORMALIZATION_V1",
                "calendarName": "XTKS", "calendarVersion": "4.11.1",
                "roundingMode": "ROUND_HALF_UP", "scale": 10,
                "fetchOptions": {"interval": "1d", "auto_adjust": false, "actions": true,
                                 "repair": false, "rounding": false},
                "qualityFlags": [], "ticker": "7203.T", "exchange": "XTKS",
                "timeZone": "Asia/Tokyo", "priceBasis": "PROVIDER_ADJUSTED_CLOSE",
                "provider": "YFINANCE", "providerVersion": "0.2.65"
              },
              "prices": [
                {"date": "2024-12-30", "close": "100.0000000000",
                 "adjustedClose": "99.1234567890", "volume": null},
                {"date": "2025-01-06", "close": "101.0000000000",
                 "adjustedClose": "100.0000000000", "volume": 3000000000}
              ]
            }
            """;
    private static final String ANALYSIS_JSON = """
            {
              "stateOrder": ["UP", "FLAT", "DOWN"], "asOfDate": "2025-01-24",
              "currentState": "UP", "sampleCount": 13, "transitionCount": 12,
              "transitionCounts": [[2,1,1],[1,2,1],[1,1,2]],
              "transitionMatrix": [[0.5,0.25,0.25],[0.25,0.5,0.25],[0.25,0.25,0.5]],
              "predictionStatus": "AVAILABLE",
              "forecasts": [
                {"horizon": 1, "probabilities": [0.5,0.25,0.25]},
                {"horizon": 3, "probabilities": [0.34375,0.328125,0.328125]},
                {"horizon": 5, "probabilities": [0.333984375,0.3330078125,0.3330078125]},
                {"horizon": 10, "probabilities": [0.33333396911621094,0.33333301544189453,0.33333301544189453]}
              ],
              "warnings": [], "engineVersion": "msa-core-v1",
              "runtime": {"configurationVersion": "analysis-config-v1", "numpyVersion": "2.2.0"}
            }
            """;
    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final AtomicReference<CapturedRequest> captured = new AtomicReference<>();
    private HttpServer server;
    private AnnotationConfigApplicationContext context;
    private PythonAnalysisClient client;
    private AnalyzeInput input;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        context = new AnnotationConfigApplicationContext();
        TestPropertyValues.of(
                "python.api.base-url=http://127.0.0.1:" + server.getAddress().getPort(),
                "INTERNAL_API_TOKEN=test-internal-token"
        ).applyTo(context);
        context.register(RestClientConfig.class, PythonAnalysisClient.class);
        context.refresh();
        client = context.getBean(PythonAnalysisClient.class);
        ConditionResponse saved = new ConditionResponse(
                101L, "Saved display name", "stock-001",
                LocalDate.parse("2025-01-06"), LocalDate.parse("2025-01-24"),
                new BigDecimal("-0.005"), new BigDecimal("0.005"), 3,
                "MLE_STRICT", "FULL", null, List.of(1, 3, 5, 10), Instant.parse("2025-01-01T00:00:00Z")
        );
        input = new AnalyzeInput(REQUEST_ID, AnalysisCondition.from(saved),
                mapper.readValue(DATASET_JSON, PriceDatasetPayload.class));
    }

    @AfterEach
    void tearDown() {
        context.close();
        server.stop(0);
    }

    @Test
    void convertsAnalyze200ToDtoAndSendsContractJsonAndHeaders() {
        respond(200, ANALYSIS_JSON);

        CalculatedAnalysis response = client.analyze(input);

        assertEquals(List.of("UP", "FLAT", "DOWN"), response.stateOrder());
        assertEquals(LocalDate.parse("2025-01-24"), response.asOfDate());
        assertEquals("UP", response.currentState());
        assertEquals(13, response.sampleCount());
        assertEquals(12, response.transitionCount());
        assertEquals(List.of(List.of(2, 1, 1), List.of(1, 2, 1), List.of(1, 1, 2)), response.transitionCounts());
        assertEquals(List.of(List.of(0.5, 0.25, 0.25), List.of(0.25, 0.5, 0.25), List.of(0.25, 0.25, 0.5)),
                response.transitionMatrix());
        assertEquals("AVAILABLE", response.predictionStatus());
        assertEquals(List.of(1, 3, 5, 10), response.forecasts().stream().map(ForecastPayload::horizon).toList());
        assertEquals(List.of(0.5, 0.25, 0.25), response.forecasts().getFirst().probabilities());
        assertEquals(List.of(), response.warnings());
        assertEquals("msa-core-v1", response.engineVersion());
        assertEquals(Map.of("configurationVersion", "analysis-config-v1", "numpyVersion", "2.2.0"),
                response.runtime());

        CapturedRequest request = captured.get();
        assertEquals("POST", request.method());
        assertEquals("application/json", request.contentType());
        assertEquals("test-internal-token", request.token());
        assertEquals(REQUEST_ID, request.requestId());
        JsonNode json = mapper.readTree(request.body());
        assertEquals(4, json.size());
        assertEquals(REQUEST_ID, json.get("requestId").asString());
        assertEquals("msa-core-v1", json.get("engineVersion").asString());
        assertEquals(mapper.readTree("""
                {"startDate":"2025-01-06", "endDate":"2025-01-24",
                 "lowerThreshold":-0.005, "upperThreshold":0.005, "stateCount":3,
                 "estimator":"MLE_STRICT", "windowMode":"FULL", "windowSize":null,
                 "horizons":[1,3,5,10]}
                """), json.get("condition"));
        assertEquals(mapper.readTree(DATASET_JSON), json.get("dataset"));
    }

    @Test
    void preservesNullMatrixRowsAndWarningsForUnavailablePrediction() {
        respond(200, """
                {
                  "stateOrder": ["UP", "FLAT", "DOWN"], "asOfDate": "2025-01-24",
                  "currentState": "UP", "sampleCount": 31, "transitionCount": 30,
                  "transitionCounts": [[15,0,0],[0,0,0],[0,0,15]],
                  "transitionMatrix": [[1.0,0.0,0.0],[null,null,null],[0.0,0.0,1.0]],
                  "predictionStatus": "UNAVAILABLE", "forecasts": [],
                  "warnings": [{"code":"ZERO_ROW_UNESTIMATED", "states":["FLAT"]}],
                  "engineVersion": "msa-core-v1", "runtime": {}
                }
                """);

        CalculatedAnalysis response = client.analyze(input);

        assertEquals("UNAVAILABLE", response.predictionStatus());
        assertEquals(Arrays.asList(null, null, null), response.transitionMatrix().get(1));
        assertEquals(List.of(), response.forecasts());
        assertEquals(List.of(new AnalysisWarning("ZERO_ROW_UNESTIMATED", List.of("FLAT"))), response.warnings());
    }

    @ParameterizedTest
    @CsvSource({"409,ENGINE_VERSION_UNSUPPORTED", "422,INSUFFICIENT_STATES",
            "429,TOO_MANY_ANALYSES", "500,CALCULATION_INVARIANT_FAILED"})
    void preservesPythonErrorResponseAndStatus(int status, String code) {
        respond(status, """
                {"code":"%s", "message":"Python analysis failed",
                 "requestId":"python-response-id",
                 "details":{"required":30,"actual":18,"states":["FLAT"],"context":{"retry":false}}}
                """.formatted(code));

        PythonApiException error = assertThrows(PythonApiException.class, () -> client.analyze(input));

        assertEquals(status, error.getStatusCode());
        assertEquals(code, error.getError().code());
        assertEquals("Python analysis failed", error.getError().message());
        assertEquals("Python analysis failed", error.getMessage());
        assertEquals("python-response-id", error.getError().requestId());
        assertEquals(Map.of("required", 30, "actual", 18, "states", List.of("FLAT"),
                "context", Map.of("retry", false)), error.getError().details());
        assertInstanceOf(RestClientResponseException.class, error.getCause());
    }

    @Test
    void convertsConnectionFailureToServiceUnavailable() {
        server.stop(0);

        AnalysisServiceUnavailableException error = assertThrows(
                AnalysisServiceUnavailableException.class, () -> client.analyze(input));

        assertInstanceOf(ResourceAccessException.class, error.getCause());
    }

    private void respond(int status, String response) {
        server.createContext("/internal/v1/analyze", exchange -> {
            captured.set(new CapturedRequest(
                    exchange.getRequestMethod(), exchange.getRequestHeaders().getFirst("Content-Type"),
                    exchange.getRequestHeaders().getFirst("X-Internal-Token"),
                    exchange.getRequestHeaders().getFirst("X-Request-Id"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
            ));
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
    }

    private record CapturedRequest(String method, String contentType, String token, String requestId, String body) {
    }
}
