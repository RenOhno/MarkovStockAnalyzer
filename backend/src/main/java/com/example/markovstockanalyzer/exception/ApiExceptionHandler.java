package com.example.markovstockanalyzer.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.List;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.regex.Pattern;

@RestControllerAdvice
public class ApiExceptionHandler {
    @Value("${INTERNAL_API_TOKEN:}")
    private String internalApiToken = "";

    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "token|password|secret|cookie|authorization|stack.?trace|traceback|path|environment", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRIVATE_TEXT = Pattern.compile(
            "token|password|secret|cookie|authorization|stack.?trace|traceback|[a-z]:[\\\\/]|/[a-zA-Z_.][^\\s]*",
            Pattern.CASE_INSENSITIVE);

    @ExceptionHandler({InvalidConditionException.class, InvalidBacktestRequestException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ApiErrorResponse> handleValidation(
            Exception exception,
            HttpServletRequest request
    ) {
        String requestId = requestId(request);
        boolean backtest = "/api/backtest".equals(request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                new ApiErrorResponse(
                        backtest ? "INVALID_BACKTEST_REQUEST" : "INVALID_CONDITION",
                        backtest ? "Backtest request is invalid" : "/api/analysis".equals(request.getRequestURI()) ? "Analysis request is invalid" : "Condition request is invalid",
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

    @ExceptionHandler(PriceDatasetNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleDatasetNotFound(
            PriceDatasetNotFoundException exception, HttpServletRequest request
    ) {
        return notFound("PRICE_DATASET_NOT_FOUND", request);
    }

    @ExceptionHandler(AnalysisResultNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleAnalysisNotFound(
            AnalysisResultNotFoundException exception, HttpServletRequest request
    ) {
        return notFound("ANALYSIS_RESULT_NOT_FOUND", request);
    }

    @ExceptionHandler(BacktestResultNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleBacktestNotFound(BacktestResultNotFoundException exception, HttpServletRequest request) {
        return notFound("BACKTEST_RESULT_NOT_FOUND", request);
    }

    @ExceptionHandler(InvalidPaginationException.class)
    public ResponseEntity<ApiErrorResponse> handlePagination(InvalidPaginationException exception, HttpServletRequest request) {
        return error(400, "INVALID_PAGINATION", "page must be nonnegative and size must be between 1 and 100", request);
    }

    @ExceptionHandler(DatasetConditionMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleDatasetMismatch(
            DatasetConditionMismatchException exception, HttpServletRequest request
    ) {
        return error(409, exception.getCode(), "Dataset does not match the analysis condition", request);
    }

    @ExceptionHandler(PythonApiException.class)
    public ResponseEntity<ApiErrorResponse> handlePython(PythonApiException exception, HttpServletRequest request) {
        ApiErrorResponse source = exception.getError();
        String requestId = source.requestId();
        if (requestId == null || !requestId.matches("[A-Za-z0-9._:-]{1,128}") || containsToken(requestId)) {
            requestId = requestId(request);
        }
        String code = source.code() != null && source.code().matches("[A-Z][A-Z0-9_]{0,63}") && !containsToken(source.code())
                ? source.code() : "ANALYSIS_SERVICE_ERROR";
        String message = source.message() != null && !privateText(source.message())
                ? source.message() : "Python analysis service request failed";
        return ResponseEntity.status(exception.getStatusCode()).body(
                new ApiErrorResponse(code, message, requestId, safeDetails(source.details()))
        );
    }

    @ExceptionHandler(AnalysisServiceUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnavailable(
            AnalysisServiceUnavailableException exception, HttpServletRequest request
    ) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                return error(504, "PROVIDER_TIMEOUT", "Analysis request timed out", request);
            }
        }
        return error(503, "ANALYSIS_SERVICE_UNAVAILABLE", "Python analysis service is unavailable", request);
    }

    @ExceptionHandler(CalculationInvariantFailedException.class)
    public ResponseEntity<ApiErrorResponse> handleInvariant(
            CalculationInvariantFailedException exception, HttpServletRequest request
    ) {
        return error(500, exception.getCode(), "Calculation result is invalid", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(
            HttpMessageNotReadableException exception, HttpServletRequest request
    ) {
        return error(400, "INVALID_JSON", "Request body is invalid", request);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(RuntimeException exception, HttpServletRequest request) {
        return error(500, "INTERNAL_SERVER_ERROR", "Request could not be completed", request);
    }

    private ResponseEntity<ApiErrorResponse> notFound(
            String code,
            HttpServletRequest request
    ) {
        String requestId = requestId(request);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                new ApiErrorResponse(code, "Requested resource was not found", requestId, Map.of())
        );
    }

    private ResponseEntity<ApiErrorResponse> error(int status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(code, message, requestId(request), Map.of()));
    }

    private String requestId(HttpServletRequest request) {
        if (request.getAttribute("requestId") instanceof String requestId) {
            return requestId;
        }
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || !requestId.matches("[A-Za-z0-9._:-]{1,128}") || containsToken(requestId)) {
            requestId = UUID.randomUUID().toString();
        }
        request.setAttribute("requestId", requestId);
        return requestId;
    }

    private Map<String, Object> safeDetails(Map<?, ?> details) {
        Map<String, Object> safe = new LinkedHashMap<>();
        if (details != null) {
            details.forEach((key, value) -> {
                if (key instanceof String text && !PRIVATE_KEY.matcher(text).find()
                        && !privateText(text)) {
                    safe.put(text, safeValue(value));
                }
            });
        }
        return safe;
    }

    private Object safeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return safeDetails(map);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::safeValue).toList();
        }
        if (value instanceof String text) {
            return privateText(text) ? null : text;
        }
        return value instanceof Number || value instanceof Boolean ? value : null;
    }

    private boolean privateText(String text) {
        return PRIVATE_TEXT.matcher(text).find() || containsToken(text);
    }

    private boolean containsToken(String text) {
        return !internalApiToken.isBlank() && text.contains(internalApiToken);
    }
}
