package com.example.markovstockanalyzer.client;

import com.example.markovstockanalyzer.config.RestClientConfig;
import com.example.markovstockanalyzer.dto.response.CalculatedBacktest;
import com.example.markovstockanalyzer.exception.*;
import com.example.markovstockanalyzer.support.BacktestFixtures;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.json.JsonMapper;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class PythonBacktestClientTests {
    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final AtomicReference<String> body = new AtomicReference<>(), method = new AtomicReference<>(),
            token = new AtomicReference<>(), requestId = new AtomicReference<>(), contentType = new AtomicReference<>();
    private HttpServer server;
    private AnnotationConfigApplicationContext context;
    private PythonAnalysisClient client;

    @BeforeEach void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); server.start();
        context = new AnnotationConfigApplicationContext();
        TestPropertyValues.of("python.api.base-url=http://127.0.0.1:" + server.getAddress().getPort(),
                "INTERNAL_API_TOKEN=backtest-test-token").applyTo(context);
        context.register(RestClientConfig.class, PythonAnalysisClient.class); context.refresh();
        client = context.getBean(PythonAnalysisClient.class);
    }
    @AfterEach void tearDown() { context.close(); server.stop(0); }

    @Test void postsExactInternalContractAndHeadersAndReadsScoredSkippedAndNullableMetrics() {
        respond(200, mapper.writeValueAsString(BacktestFixtures.mixed()));
        CalculatedBacktest result = client.backtest(BacktestFixtures.input());
        assertEquals(BacktestFixtures.mixed(), result);
        assertEquals("POST", method.get());
        assertEquals("application/json", contentType.get());
        assertEquals("backtest-test-token", token.get());
        assertEquals(BacktestFixtures.input().requestId(), requestId.get());
        var json = mapper.readTree(body.get());
        assertEquals(5, json.size());
        assertEquals("backtest-request-001", json.get("requestId").asString());
        assertEquals("msa-core-v1", json.get("engineVersion").asString());
        assertEquals(mapper.readTree("""
                {"testStart":"2025-02-20","testEnd":"2025-02-21","minTrainStates":30,
                 "trainingMode":"EXPANDING","windowSize":null,"horizon":1}
                """), json.get("evaluation"));
        assertEquals(mapper.readTree("""
                {"startDate":"2025-01-06","endDate":"2025-02-28","lowerThreshold":-0.005,"upperThreshold":0.005,
                 "stateCount":3,"estimator":"MLE_STRICT","windowMode":"FULL","windowSize":null,"horizons":[1,3,5,10]}
                """), json.get("condition"));
        assertEquals(13, json.get("dataset").size());
        assertEquals("7203.T", json.get("dataset").get("ticker").asString());
        assertEquals("100.0000000000", json.get("dataset").get("prices").get(0).get("close").asString());
        assertEquals(mapper.valueToTree(BacktestFixtures.dataset()), json.get("dataset"));
        assertFalse(json.has("conditionId")); assertFalse(json.has("datasetId"));
        assertEquals("SCORED", result.predictions().getFirst().status());
        assertNull(result.predictions().getLast().probabilities());
        assertNull(result.summary().metrics().precision().get(1));
    }
    @Test void preservesAllSkippedNullMetrics() {
        respond(200, mapper.writeValueAsString(BacktestFixtures.allSkipped()));
        assertEquals(BacktestFixtures.allSkipped(), client.backtest(BacktestFixtures.input()));
    }
    @ParameterizedTest @CsvSource({"422,INSUFFICIENT_TRAINING_DATA","409,DATASET_CONDITION_MISMATCH",
            "429,TOO_MANY_ANALYSES","504,PROVIDER_TIMEOUT","500,CALCULATION_INVARIANT_FAILED"})
    void preservesUnifiedPythonError(int status, String code) {
        respond(status, """
                {"code":"%s","message":"Backtest request failed","requestId":"python-error",
                 "details":{"required":30,"actual":18}}
                """.formatted(code));
        var error = assertThrows(PythonApiException.class, () -> client.backtest(BacktestFixtures.input()));
        assertEquals(status, error.getStatusCode()); assertEquals(code, error.getError().code());
        assertEquals("Backtest request failed", error.getMessage()); assertEquals("python-error", error.getError().requestId());
        assertEquals(Map.of("required", 30, "actual", 18), error.getError().details());
    }
    @Test void connectionFailureBecomesServiceUnavailable() {
        server.stop(0);
        var error = assertThrows(AnalysisServiceUnavailableException.class, () -> client.backtest(BacktestFixtures.input()));
        assertInstanceOf(ResourceAccessException.class, error.getCause());
    }
    private void respond(int status, String response) {
        server.createContext("/internal/v1/backtest", exchange -> {
            method.set(exchange.getRequestMethod()); token.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            requestId.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
    }
}
