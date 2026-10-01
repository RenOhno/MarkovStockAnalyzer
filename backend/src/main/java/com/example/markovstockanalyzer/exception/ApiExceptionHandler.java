package com.example.markovstockanalyzer.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({InvalidConditionException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ApiErrorResponse> handleValidation(
            Exception exception,
            HttpServletRequest request
    ) {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                new ApiErrorResponse(
                        "INVALID_CONDITION",
                        "Condition request is invalid",
                        requestId,
                        Map.of()
                )
        );
    }

    @ExceptionHandler(StockNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleStockNotFound(
            StockNotFoundException exception,
            HttpServletRequest request
    ) {
        return notFound("STOCK_NOT_FOUND", request);
    }

    @ExceptionHandler(ConditionNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleConditionNotFound(
            ConditionNotFoundException exception,
            HttpServletRequest request
    ) {
        return notFound("CONDITION_NOT_FOUND", request);
    }

    private ResponseEntity<ApiErrorResponse> notFound(
            String code,
            HttpServletRequest request
    ) {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                new ApiErrorResponse(code, "Requested resource was not found", requestId, Map.of())
        );
    }
}
