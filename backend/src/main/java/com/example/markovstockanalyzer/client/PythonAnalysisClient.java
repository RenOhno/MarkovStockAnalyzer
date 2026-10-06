package com.example.markovstockanalyzer.client;

import com.example.markovstockanalyzer.dto.request.AnalyzeInput;
import com.example.markovstockanalyzer.dto.request.BacktestInput;
import com.example.markovstockanalyzer.dto.response.CalculatedBacktest;
import com.example.markovstockanalyzer.dto.request.FetchPricesRequest;
import com.example.markovstockanalyzer.dto.request.SeriesInput;
import com.example.markovstockanalyzer.dto.response.CalculatedAnalysis;
import com.example.markovstockanalyzer.dto.response.CalculatedSeries;
import com.example.markovstockanalyzer.dto.response.PriceDatasetPayload;
import com.example.markovstockanalyzer.dto.response.PythonHealthResponse;
import com.example.markovstockanalyzer.exception.AnalysisServiceUnavailableException;
import com.example.markovstockanalyzer.exception.ApiErrorResponse;
import com.example.markovstockanalyzer.exception.PythonApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
    private final RestClient analyzeRestClient;
    private final RestClient backtestRestClient;
    private final String internalApiToken;

    public PythonAnalysisClient(
            RestClient pythonRestClient,
            String internalApiToken
    ) {
        this(pythonRestClient, pythonRestClient, internalApiToken);
    }

    public PythonAnalysisClient(
            @Qualifier("pythonRestClient") RestClient pythonRestClient,
            @Qualifier("pythonAnalyzeRestClient") RestClient pythonAnalyzeRestClient,
            @Value("${INTERNAL_API_TOKEN:}") String internalApiToken
    ) {
        this(pythonRestClient, pythonAnalyzeRestClient, pythonAnalyzeRestClient, internalApiToken);
    }

    @Autowired
    public PythonAnalysisClient(
            @Qualifier("pythonRestClient") RestClient pythonRestClient,
            @Qualifier("pythonAnalyzeRestClient") RestClient pythonAnalyzeRestClient,
            @Qualifier("pythonBacktestRestClient") RestClient pythonBacktestRestClient,
            @Value("${INTERNAL_API_TOKEN:}") String internalApiToken
    ) {
        this.restClient = pythonRestClient;
        this.analyzeRestClient = pythonAnalyzeRestClient;
        this.backtestRestClient = pythonBacktestRestClient;
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

    public CalculatedAnalysis analyze(AnalyzeInput input) {
        try {
            return analyzeRestClient.post()
                    .uri("/internal/v1/analyze")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Token", internalApiToken)
                    .header("X-Request-Id", input.requestId())
                    .body(input)
                    .retrieve()
                    .body(CalculatedAnalysis.class);
        } catch (RestClientResponseException exception) {
            ApiErrorResponse fallback = new ApiErrorResponse(
                    "ANALYSIS_SERVICE_ERROR", "Python analysis service request failed",
                    input.requestId(), Map.of()
            );
            throw new PythonApiException(
                    exception.getStatusCode().value(), pythonError(exception, fallback), exception
            );
        } catch (RestClientException exception) {
            throw new AnalysisServiceUnavailableException(exception);
        }
    }

    public CalculatedSeries series(SeriesInput input) {
        // Assumption: no series-specific deadline is defined in design section 15.4.
        // Reuse the normal-analysis client: connect 2 seconds, response 5 seconds.
        try {
            return analyzeRestClient.post()
                    .uri("/internal/v1/series")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Token", internalApiToken)
                    .header("X-Request-Id", input.requestId())
                    .body(input)
                    .retrieve()
                    .body(CalculatedSeries.class);
        } catch (RestClientResponseException exception) {
            ApiErrorResponse fallback = new ApiErrorResponse(
                    "ANALYSIS_SERVICE_ERROR", "Python analysis service request failed",
                    input.requestId(), Map.of()
            );
            throw new PythonApiException(
                    exception.getStatusCode().value(), pythonError(exception, fallback), exception
            );
        } catch (RestClientException exception) {
            throw new AnalysisServiceUnavailableException(exception);
        }
    }

    public CalculatedBacktest backtest(BacktestInput input) {
        try {
            return backtestRestClient.post().uri("/internal/v1/backtest")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Token", internalApiToken)
                    .header("X-Request-Id", input.requestId())
                    .body(input).retrieve().body(CalculatedBacktest.class);
        } catch (RestClientResponseException exception) {
            ApiErrorResponse fallback = new ApiErrorResponse("ANALYSIS_SERVICE_ERROR",
                    "Python analysis service request failed", input.requestId(), Map.of());
            throw new PythonApiException(exception.getStatusCode().value(), pythonError(exception, fallback), exception);
        } catch (RestClientException exception) {
            throw new AnalysisServiceUnavailableException(exception);
        }
    }

    private ApiErrorResponse pythonError(RestClientResponseException exception, ApiErrorResponse fallback) {
        try {
            ApiErrorResponse error = exception.getResponseBodyAs(ApiErrorResponse.class);
            if (error != null && error.code() != null && !error.code().isBlank()
                    && error.message() != null && !error.message().isBlank()
                    && error.details() != null) {
                return new ApiErrorResponse(
                        error.code(), error.message(),
                        error.requestId() == null ? fallback.requestId() : error.requestId(), error.details()
                );
            }
        } catch (RuntimeException ignored) {
            // Non-contract bodies (including provider HTML) must not become public errors.
        }
        return fallback;
    }

    private ApiErrorResponse pythonError(RestClientResponseException exception, String requestId) {
        ApiErrorResponse fallback = switch (exception.getStatusCode().value()) {
            case 422 -> new ApiErrorResponse(
                    "INVALID_CONDITION", "Price fetch request is invalid", requestId, Map.of());
            case 502 -> new ApiErrorResponse(
                    "PROVIDER_UNAVAILABLE", "Price provider unavailable", requestId, Map.of());
            case 504 -> new ApiErrorResponse(
                    "PROVIDER_TIMEOUT", "Price provider timed out", requestId, Map.of());
            default -> new ApiErrorResponse(
                    "ANALYSIS_SERVICE_ERROR", "Python analysis service request failed", requestId, Map.of());
        };
        return pythonError(exception, fallback);
    }
}
