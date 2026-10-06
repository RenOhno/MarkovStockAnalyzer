package com.example.markovstockanalyzer.client;

import com.example.markovstockanalyzer.dto.request.FetchPricesRequest;
import com.example.markovstockanalyzer.dto.response.PriceDatasetPayload;
import com.example.markovstockanalyzer.dto.response.PythonHealthResponse;
import com.example.markovstockanalyzer.exception.AnalysisServiceUnavailableException;
import com.example.markovstockanalyzer.exception.ApiErrorResponse;
import com.example.markovstockanalyzer.exception.PythonApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.util.Map;

@Component
public class PythonAnalysisClient {
    private final RestClient restClient;
    private final String internalApiToken;

    public PythonAnalysisClient(
            RestClient pythonRestClient,
            @Value("${INTERNAL_API_TOKEN:}") String internalApiToken
    ) {
        this.restClient = pythonRestClient;
        this.internalApiToken = internalApiToken;
    }

    public PythonHealthResponse health(String requestId) {
        try {
            return restClient.get()
                    .uri("/internal/v1/health")
                    .header("X-Internal-Token", internalApiToken)
                    .header("X-Request-Id", requestId)
                    .retrieve()
                    .body(PythonHealthResponse.class);
        } catch (RestClientException exception) {
            throw new AnalysisServiceUnavailableException(exception);
        }
    }

    public PriceDatasetPayload fetchPrices(FetchPricesRequest request) {
        try {
            return restClient.post()
                    .uri("/internal/v1/prices/fetch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Token", internalApiToken)
                    .header("X-Request-Id", request.requestId())
                    .body(request)
                    .retrieve()
                    .body(PriceDatasetPayload.class);
        } catch (RestClientResponseException exception) {
            throw new PythonApiException(
                    exception.getStatusCode().value(),
                    pythonError(exception, request.requestId()),
                    exception
            );
        } catch (RestClientException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof SocketTimeoutException) {
                    throw new PythonApiException(504, new ApiErrorResponse(
                            "PROVIDER_TIMEOUT", "Price fetch timed out", request.requestId(), Map.of()
                    ), exception);
                }
            }
            throw new AnalysisServiceUnavailableException(exception);
        }
    }

    private ApiErrorResponse pythonError(RestClientResponseException exception, String requestId) {
        try {
            ApiErrorResponse error = exception.getResponseBodyAs(ApiErrorResponse.class);
            if (error != null && error.code() != null && !error.code().isBlank()
                    && error.message() != null && !error.message().isBlank()
                    && error.details() != null) {
                return new ApiErrorResponse(error.code(), error.message(), requestId, error.details());
            }
        } catch (RuntimeException ignored) {
            // Non-contract bodies (including provider HTML) must not become public errors.
        }
        return switch (exception.getStatusCode().value()) {
            case 422 -> new ApiErrorResponse(
                    "INVALID_CONDITION", "Price fetch request is invalid", requestId, Map.of());
            case 502 -> new ApiErrorResponse(
                    "PROVIDER_UNAVAILABLE", "Price provider unavailable", requestId, Map.of());
            case 504 -> new ApiErrorResponse(
                    "PROVIDER_TIMEOUT", "Price provider timed out", requestId, Map.of());
            default -> new ApiErrorResponse(
                    "ANALYSIS_SERVICE_ERROR", "Python analysis service request failed", requestId, Map.of());
        };
    }
}
