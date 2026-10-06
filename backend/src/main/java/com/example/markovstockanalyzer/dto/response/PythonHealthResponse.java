package com.example.markovstockanalyzer.dto.response;

public record PythonHealthResponse(
        String status,
        String engineVersion
) {
}
