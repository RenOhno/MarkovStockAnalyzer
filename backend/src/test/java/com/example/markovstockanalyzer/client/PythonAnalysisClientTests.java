package com.example.markovstockanalyzer.client;

import com.example.markovstockanalyzer.dto.response.PythonHealthResponse;
import com.example.markovstockanalyzer.exception.AnalysisServiceUnavailableException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
