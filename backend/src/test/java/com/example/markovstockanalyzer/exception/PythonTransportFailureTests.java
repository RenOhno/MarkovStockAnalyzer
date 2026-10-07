package com.example.markovstockanalyzer.exception;

import com.example.markovstockanalyzer.client.PythonAnalysisClient;
import com.example.markovstockanalyzer.dto.request.FetchPricesRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestClient;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class PythonTransportFailureTests {
    private final FetchPricesRequest request = new FetchPricesRequest("transport-request", "TEST", "XTKS", "Asia/Tokyo",
            LocalDate.parse("2025-01-06"), LocalDate.parse("2025-06-30"), true, "PROVIDER_ADJUSTED_CLOSE", "YFINANCE");

    private SocketTimeoutException socketTimeout(boolean connecting) {
        var error = new SocketTimeoutException("unrelated or localized message");
        error.setStackTrace(new StackTraceElement[] { new StackTraceElement("sun.nio.ch.NioSocketImpl",
                connecting ? "timedFinishConnect" : "timedRead", "NioSocketImpl.java", 1) });
        return error;
    }
    private PythonAnalysisClient client(SocketTimeoutException failure) {
        var rest = RestClient.builder().baseUrl("http://offline.test").requestFactory((uri, method) -> { throw failure; }).build();
        return new PythonAnalysisClient(rest, "test-only-token");
    }
    @Test void jdkSocketConnectTimeoutIsConnectionFailureWithoutMessageMatching() {
        assertTrue(PythonTransportFailure.isConnectionFailure(socketTimeout(true)));
        assertFalse(PythonTransportFailure.isResponseTimeout(socketTimeout(true)));
    }
    @Test void jdkSocketReadTimeoutRemainsResponseTimeout() {
        assertFalse(PythonTransportFailure.isConnectionFailure(socketTimeout(false)));
        assertTrue(PythonTransportFailure.isResponseTimeout(socketTimeout(false)));
    }
    @Test void typedHttpConnectAndResponseTimeoutsRemainDistinct() {
        assertFalse(PythonTransportFailure.isResponseTimeout(new HttpConnectTimeoutException("connect")));
        assertTrue(PythonTransportFailure.isResponseTimeout(new HttpTimeoutException("response")));
    }
    @Test void priceFetchConnectionTimeoutBecomesUnavailableAndPublic503WithRequestId() {
        var error = assertThrows(AnalysisServiceUnavailableException.class, () -> client(socketTimeout(true)).fetchPrices(request));
        var servlet = new MockHttpServletRequest(); servlet.setAttribute("requestId", request.requestId());
        var response = new ApiExceptionHandler().handleUnavailable(error, servlet);
        assertEquals(503, response.getStatusCode().value()); assertEquals("ANALYSIS_SERVICE_UNAVAILABLE", response.getBody().code());
        assertEquals(request.requestId(), response.getBody().requestId());
    }
    @Test void priceFetchReadTimeoutKeeps504AndProviderTimeoutPayload() {
        var error = assertThrows(PythonApiException.class, () -> client(socketTimeout(false)).fetchPrices(request));
        assertEquals(504, error.getStatusCode()); assertEquals("PROVIDER_TIMEOUT", error.getError().code());
        assertEquals(request.requestId(), error.getError().requestId());
    }
    @Test void analysisReadTimeoutStillMapsToPublic504() {
        var response = new ApiExceptionHandler().handleUnavailable(new AnalysisServiceUnavailableException(socketTimeout(false)), new MockHttpServletRequest());
        assertEquals(504, response.getStatusCode().value()); assertEquals("PROVIDER_TIMEOUT", response.getBody().code());
    }
}
