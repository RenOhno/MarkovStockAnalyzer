package com.example.markovstockanalyzer.client;

import com.example.markovstockanalyzer.dto.request.FetchPricesRequest;
import com.example.markovstockanalyzer.dto.response.PriceDatasetPayload;
import com.example.markovstockanalyzer.dto.response.PricePoint;
import com.example.markovstockanalyzer.exception.PythonApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PythonPricesClientTests {
    private static final FetchPricesRequest REQUEST = new FetchPricesRequest(
            "req-example-001", "7203.T", "XTKS", "Asia/Tokyo",
            LocalDate.parse("2025-01-06"), LocalDate.parse("2025-12-30"), true,
            "PROVIDER_ADJUSTED_CLOSE", "YFINANCE"
    );
    private static final String REQUEST_JSON = """
            {
              "requestId": "req-example-001", "ticker": "7203.T",
              "exchange": "XTKS", "timeZone": "Asia/Tokyo",
              "startDate": "2025-01-06", "endDate": "2025-12-30",
              "includePreviousSession": true,
              "priceBasis": "PROVIDER_ADJUSTED_CLOSE", "provider": "YFINANCE"
            }
            """;
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
    private MockRestServiceServer server;
    private PythonAnalysisClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://python.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PythonAnalysisClient(builder.build(), "test-internal-token");
    }

    @Test
    void postsContractJsonAndHeadersAndDeserializesCompleteDataset() {
        server.expect(requestTo("http://python.test/internal/v1/prices/fetch"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(REQUEST_JSON, JsonCompareMode.STRICT))
                .andExpect(header("X-Internal-Token", "test-internal-token"))
                .andExpect(header("X-Request-Id", REQUEST.requestId()))
                .andRespond(withSuccess(DATASET_JSON, MediaType.APPLICATION_JSON));

        PriceDatasetPayload response = client.fetchPrices(REQUEST);

        assertEquals("7203.T", response.ticker());
        assertEquals("XTKS", response.exchange());
        assertEquals("Asia/Tokyo", response.timeZone());
        assertEquals("PROVIDER_ADJUSTED_CLOSE", response.priceBasis());
        assertEquals("YFINANCE", response.provider());
        assertEquals("0.2.65", response.providerVersion());
        assertEquals("PROVIDER_ADJUSTED_CLOSE_V1", response.adjustmentPolicy());
        assertEquals(OffsetDateTime.parse("2026-10-06T01:02:03Z"), response.fetchedAt());
        assertEquals(LocalDate.parse("2024-12-30"), response.coverageStart());
        assertEquals(LocalDate.parse("2025-01-06"), response.coverageEnd());
        assertEquals("a".repeat(64), response.contentSha256());
        assertEquals(Map.ofEntries(
                Map.entry("schemaVersion", 1), Map.entry("normalizationVersion", "NORMALIZATION_V1"),
                Map.entry("calendarName", "XTKS"), Map.entry("calendarVersion", "4.11.1"),
                Map.entry("roundingMode", "ROUND_HALF_UP"), Map.entry("scale", 10),
                Map.entry("fetchOptions", Map.of("interval", "1d", "auto_adjust", false,
                        "actions", true, "repair", false, "rounding", false)),
                Map.entry("qualityFlags", List.of()), Map.entry("ticker", "7203.T"),
                Map.entry("exchange", "XTKS"), Map.entry("timeZone", "Asia/Tokyo"),
                Map.entry("priceBasis", "PROVIDER_ADJUSTED_CLOSE"),
                Map.entry("provider", "YFINANCE"), Map.entry("providerVersion", "0.2.65")
        ), response.metadata());
        assertEquals(List.of(
                new PricePoint(LocalDate.parse("2024-12-30"), "100.0000000000", "99.1234567890", null),
                new PricePoint(LocalDate.parse("2025-01-06"), "101.0000000000", "100.0000000000", 3000000000L)
        ), response.prices());
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({"422,NO_PRICE_DATA", "502,PROVIDER_UNAVAILABLE", "504,PROVIDER_TIMEOUT"})
    void preservesUnifiedPythonErrors(int status, String code) {
        server.expect(requestTo("http://python.test/internal/v1/prices/fetch"))
                .andRespond(withStatus(HttpStatus.valueOf(status))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code": "%s", "message": "Price fetch failed",
                                 "requestId": "req-example-001", "details": {"required": 30}}
                                """.formatted(code)));

        PythonApiException error = assertThrows(PythonApiException.class, () -> client.fetchPrices(REQUEST));

        assertEquals(status, error.getStatusCode());
        assertEquals(code, error.getError().code());
        assertEquals("Price fetch failed", error.getMessage());
        assertEquals(REQUEST.requestId(), error.getError().requestId());
        assertEquals(Map.of("required", 30), error.getError().details());
        assertInstanceOf(RestClientResponseException.class, error.getCause());
        server.verify();
    }

    @Test
    void normalizesNativeFastApiValidationErrorWithoutLeakingInput() {
        server.expect(requestTo("http://python.test/internal/v1/prices/fetch"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"detail": [{"loc": ["body", "ticker"], "type": "value_error",
                                             "msg": "internal path /secret", "input": "secret"}]}
                                """));

        PythonApiException error = assertThrows(PythonApiException.class, () -> client.fetchPrices(REQUEST));

        assertEquals(422, error.getStatusCode());
        assertEquals("INVALID_CONDITION", error.getError().code());
        assertEquals("Price fetch request is invalid", error.getError().message());
        assertEquals(REQUEST.requestId(), error.getError().requestId());
        assertEquals(Map.of(), error.getError().details());
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({"502,PROVIDER_UNAVAILABLE", "504,PROVIDER_TIMEOUT", "500,ANALYSIS_SERVICE_ERROR"})
    void replacesNonJsonErrorsWithSafeMessages(int status, String code) {
        server.expect(requestTo("http://python.test/internal/v1/prices/fetch"))
                .andRespond(withStatus(HttpStatus.valueOf(status))
                        .contentType(MediaType.TEXT_HTML).body("<html>Cookie: secret; /internal/path</html>"));

        PythonApiException error = assertThrows(PythonApiException.class, () -> client.fetchPrices(REQUEST));

        assertEquals(status, error.getStatusCode());
        assertEquals(code, error.getError().code());
        assertEquals(REQUEST.requestId(), error.getError().requestId());
        assertEquals(Map.of(), error.getError().details());
        assertFalse(error.getMessage().contains("secret"));
        assertFalse(error.getMessage().contains("/internal/path"));
        server.verify();
    }
}
