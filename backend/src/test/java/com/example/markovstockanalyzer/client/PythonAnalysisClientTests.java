package com.example.markovstockanalyzer.client;

import com.example.markovstockanalyzer.dto.request.FetchPricesRequest;
import com.example.markovstockanalyzer.dto.response.PythonHealthResponse;
import com.example.markovstockanalyzer.exception.AnalysisServiceUnavailableException;
import com.example.markovstockanalyzer.exception.PythonApiException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PythonAnalysisClientTests {
    private HttpServer server;
    private AtomicReference<String> internalToken;
    private AtomicReference<String> requestId;
    private PythonAnalysisClient client;

    @BeforeEach
    void setUp() throws IOException {
        internalToken = new AtomicReference<>();
        requestId = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/health", exchange -> {
            internalToken.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            requestId.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
            byte[] body = "{\"status\":\"UP\",\"engineVersion\":\"msa-core-v1\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        RestClient restClient = RestClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .build();
        client = new PythonAnalysisClient(restClient, "test-internal-token");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void convertsHealthResponseAndSendsHeaders() {
        PythonHealthResponse response = client.health("request-123");

        assertEquals("UP", response.status());
        assertEquals("msa-core-v1", response.engineVersion());
        assertEquals("test-internal-token", internalToken.get());
        assertEquals("request-123", requestId.get());
    }

    @Test
    void convertsConnectionFailureToServiceUnavailable() {
        RestClient unavailable = RestClient.builder()
                .baseUrl("http://127.0.0.1:1")
                .build();
        PythonAnalysisClient unavailableClient = new PythonAnalysisClient(
                unavailable,
                "test-internal-token"
        );

        assertThrows(
                AnalysisServiceUnavailableException.class,
                () -> unavailableClient.health("request-failure")
        );
    }

    @Test
    void convertsPricesConnectionFailureToServiceUnavailable() throws IOException {
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(15));
        PythonAnalysisClient unavailableClient = new PythonAnalysisClient(
                RestClient.builder().baseUrl("http://127.0.0.1:" + unusedPort)
                        .requestFactory(factory).build(),
                "test-internal-token"
        );

        AnalysisServiceUnavailableException error = assertThrows(
                AnalysisServiceUnavailableException.class,
                () -> unavailableClient.fetchPrices(pricesRequest())
        );

        assertInstanceOf(ResourceAccessException.class, error.getCause());
    }

    @Test
    void convertsPricesReadTimeoutWithoutRetryingPost() {
        CountDownLatch releaseResponse = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        server.createContext("/internal/v1/prices/fetch", exchange -> {
            attempts.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            try {
                releaseResponse.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        // Exercise a real socket timeout with a short deadline; production durations
        // are checked separately in RestClientConfigTests.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofMillis(200));
        PythonAnalysisClient timeoutClient = new PythonAnalysisClient(
                RestClient.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                        .requestFactory(factory).build(),
                "test-internal-token"
        );

        try {
            PythonApiException error = assertThrows(
                    PythonApiException.class,
                    () -> timeoutClient.fetchPrices(pricesRequest())
            );

            assertEquals(504, error.getStatusCode());
            assertEquals("PROVIDER_TIMEOUT", error.getError().code());
            assertEquals("request-prices", error.getError().requestId());
            assertInstanceOf(RestClientException.class, error.getCause());
            assertInstanceOf(SocketTimeoutException.class, error.getCause().getCause());
            assertEquals(1, attempts.get());
        } finally {
            releaseResponse.countDown();
        }
    }

    private FetchPricesRequest pricesRequest() {
        return new FetchPricesRequest(
                "request-prices", "7203.T", "XTKS", "Asia/Tokyo",
                LocalDate.parse("2025-01-06"), LocalDate.parse("2025-12-30"), true,
                "PROVIDER_ADJUSTED_CLOSE", "YFINANCE"
        );
    }
}
