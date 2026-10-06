package com.example.markovstockanalyzer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {
    @Bean
    RestClient pythonRestClient(
            @Value("${python.api.base-url:http://127.0.0.1:8000}") String baseUrl
    ) {
        return createClient(baseUrl, Duration.ofSeconds(15));
    }

    @Bean
    RestClient pythonAnalyzeRestClient(
            @Value("${python.api.base-url:http://127.0.0.1:8000}") String baseUrl
    ) {
        return createClient(baseUrl, Duration.ofSeconds(5));
    }

    @Bean
    RestClient pythonBacktestRestClient(
            @Value("${python.api.base-url:http://127.0.0.1:8000}") String baseUrl
    ) {
        return createClient(baseUrl, Duration.ofSeconds(12));
    }

    private RestClient createClient(String baseUrl, Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
