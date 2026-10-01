package com.example.markovstockanalyzer.exception;

import java.util.Map;

public record ApiErrorResponse(
        String code,
        String message,
        String requestId,
        Map<String, Object> details
) {
}
