package com.example.markovstockanalyzer.config;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;

class RestClientConfigTests {
    @Test
    void configuresAnalyzeWithTwoSecondConnectionAndFiveSecondResponseTimeouts() {
        try (MockedConstruction<SimpleClientHttpRequestFactory> factories =
                     mockConstruction(SimpleClientHttpRequestFactory.class)) {
            new RestClientConfig().pythonAnalyzeRestClient("http://127.0.0.1:8000");

            assertEquals(1, factories.constructed().size());
            SimpleClientHttpRequestFactory factory = factories.constructed().getFirst();
            verify(factory).setConnectTimeout(Duration.ofSeconds(2));
            verify(factory).setReadTimeout(Duration.ofSeconds(5));
        }
    }

    @Test
    void configuresTwoSecondConnectionAndFifteenSecondResponseTimeouts() {
        try (MockedConstruction<SimpleClientHttpRequestFactory> factories =
                     mockConstruction(SimpleClientHttpRequestFactory.class)) {
            new RestClientConfig().pythonRestClient("http://127.0.0.1:8000");

            assertEquals(1, factories.constructed().size());
            SimpleClientHttpRequestFactory factory = factories.constructed().getFirst();
            verify(factory).setConnectTimeout(Duration.ofSeconds(2));
            verify(factory).setReadTimeout(Duration.ofSeconds(15));
        }
    }
}
