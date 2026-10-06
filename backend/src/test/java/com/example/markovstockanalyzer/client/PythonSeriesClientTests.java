package com.example.markovstockanalyzer.client;

import com.example.markovstockanalyzer.config.RestClientConfig;
import com.example.markovstockanalyzer.dto.request.AnalysisCondition;
import com.example.markovstockanalyzer.dto.request.AnalyzeInput;
import com.example.markovstockanalyzer.dto.request.SeriesInput;
import com.example.markovstockanalyzer.dto.response.CalculatedSeries;
import com.example.markovstockanalyzer.dto.response.PriceDatasetPayload;
import com.example.markovstockanalyzer.dto.response.SeriesPoint;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PythonSeriesClientTests {
    private static final String REQUEST_ID = "request-series-001";
    private static final String DATASET_JSON = """
            {
              "ticker": "7203.T", "exchange": "XTKS", "timeZone": "Asia/Tokyo",
              "priceBasis": "PROVIDER_ADJUSTED_CLOSE", "provider": "YFINANCE",
              "providerVersion": "0.2.65", "adjustmentPolicy": "PROVIDER_ADJUSTED_CLOSE_V1",
              "fetchedAt": "2026-10-06T01:02:03Z",
              "coverageStart": "2024-12-30", "coverageEnd": "2025-01-08",
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
                {"date": "2024-12-30", "close": "99.0000000000", "adjustedClose": "98.0000000000", "volume": null},
                {"date": "2025-01-06", "close": "100.0000000000", "adjustedClose": "99.0000000000", "volume": 3000000000},
                {"date": "2025-01-07", "close": "100.5000000000", "adjustedClose": "99.4950000000", "volume": 0},
                {"date": "2025-01-08", "close": "99.9000000000", "adjustedClose": "98.8980300000", "volume": 100}
              ]
            }
            """;
    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final AtomicReference<CapturedRequest> captured = new AtomicReference<>();
    private HttpServer server;
    private AnnotationConfigApplicationContext context;
    private PythonAnalysisClient client;
    private AnalyzeInput analyzeInput;

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
        AnalysisCondition condition = new AnalysisCondition(
                LocalDate.parse("2025-01-06"), LocalDate.parse("2025-01-08"),
                new BigDecimal("-0.005"), new BigDecimal("0.005"), 3,
                "MLE_STRICT", "FULL", null, List.of(1, 3, 5, 10));
        analyzeInput = new AnalyzeInput(REQUEST_ID, condition, mapper.readValue(DATASET_JSON, PriceDatasetPayload.class));
    }

    @AfterEach
    void tearDown() {
        context.close();
        server.stop(0);
    }

    @Test
    void convertsSeries200AndPostsCompleteContractJsonAndHeaders() {
        respond(200, """
                {
                  "priceBasis": "PROVIDER_ADJUSTED_CLOSE", "engineVersion": "msa-core-v1",
                  "points": [
                    {"date":"2025-01-06", "close":"100.0000000000", "adjustedClose":"99.0000000000",
                     "returnValue":0.01020408163265306, "state":"UP"},
                    {"date":"2025-01-07", "close":"100.5000000000", "adjustedClose":"99.4950000000",
                     "returnValue":0.005, "state":"FLAT"},
                    {"date":"2025-01-08", "close":"99.9000000000", "adjustedClose":"98.8980300000",
                     "returnValue":-0.006, "state":"DOWN"}
                  ]
                }
                """);
        SeriesInput input = new SeriesInput(analyzeInput, "msa-core-v1");

        CalculatedSeries response = client.series(input);

        assertEquals(new CalculatedSeries("PROVIDER_ADJUSTED_CLOSE", List.of(
                new SeriesPoint(LocalDate.parse("2025-01-06"), "100.0000000000", "99.0000000000", 0.01020408163265306, "UP"),
                new SeriesPoint(LocalDate.parse("2025-01-07"), "100.5000000000", "99.4950000000", 0.005, "FLAT"),
                new SeriesPoint(LocalDate.parse("2025-01-08"), "99.9000000000", "98.8980300000", -0.006, "DOWN")
        ), "msa-core-v1"), response);
        assertSame(analyzeInput.condition(), input.condition());
        assertSame(analyzeInput.dataset(), input.dataset());
        CapturedRequest request = captured.get();
        assertEquals("POST", request.method());
        assertEquals("application/json", request.contentType());
        assertEquals("test-internal-token", request.token());
        assertEquals(REQUEST_ID, request.requestId());
        JsonNode json = mapper.readTree(request.body());
        assertEquals(5, json.size());
        assertEquals(REQUEST_ID, json.get("requestId").asString());
        assertEquals("msa-core-v1", json.get("engineVersion").asString());
        assertEquals("msa-core-v1", json.get("requiredEngineVersion").asString());
        assertEquals(mapper.readTree("""
                {"startDate":"2025-01-06", "endDate":"2025-01-08",
                 "lowerThreshold":-0.005, "upperThreshold":0.005, "stateCount":3,
                 "estimator":"MLE_STRICT", "windowMode":"FULL", "windowSize":null, "horizons":[1,3,5,10]}
                """), json.get("condition"));
        assertEquals(mapper.readTree(DATASET_JSON), json.get("dataset"));
    }

    @ParameterizedTest
    @CsvSource({"409,ENGINE_VERSION_UNSUPPORTED", "422,INVALID_DATASET",
            "429,TOO_MANY_ANALYSES", "500,CALCULATION_INVARIANT_FAILED"})
    void preservesPythonErrorsAndSendsRequiredVersionWithoutReplacingIt(int status, String code) {
        respond(status, """
                {"code":"%s", "message":"Series request failed", "requestId":"python-response-id",
                 "details":{"versions":{"required":"msa-core-v0","supported":"msa-core-v1"}}}
                """.formatted(code));
        // The caller can pass the engineVersion stored with an AnalysisResult, including an older version.
        SeriesInput input = new SeriesInput(analyzeInput, "msa-core-v0");

        PythonApiException error = assertThrows(PythonApiException.class, () -> client.series(input));

        assertEquals(status, error.getStatusCode());
        assertEquals(code, error.getError().code());
        assertEquals("Series request failed", error.getMessage());
        assertEquals("python-response-id", error.getError().requestId());
        assertEquals(Map.of("versions", Map.of("required", "msa-core-v0", "supported", "msa-core-v1")),
                error.getError().details());
        assertInstanceOf(RestClientResponseException.class, error.getCause());
        JsonNode request = mapper.readTree(captured.get().body());
        assertEquals("msa-core-v1", request.get("engineVersion").asString());
        assertEquals("msa-core-v0", request.get("requiredEngineVersion").asString());
    }

    @Test
    void convertsConnectionFailureToServiceUnavailable() {
        server.stop(0);

        AnalysisServiceUnavailableException error = assertThrows(AnalysisServiceUnavailableException.class,
                () -> client.series(new SeriesInput(analyzeInput, "msa-core-v1")));

        assertInstanceOf(ResourceAccessException.class, error.getCause());
    }

    private void respond(int status, String response) {
        server.createContext("/internal/v1/series", exchange -> {
            captured.set(new CapturedRequest(
                    exchange.getRequestMethod(), exchange.getRequestHeaders().getFirst("Content-Type"),
                    exchange.getRequestHeaders().getFirst("X-Internal-Token"),
                    exchange.getRequestHeaders().getFirst("X-Request-Id"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
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
