package com.example.markovstockanalyzer.client;

import com.example.markovstockanalyzer.dto.response.PythonHealthResponse;
import com.example.markovstockanalyzer.exception.AnalysisServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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
}
