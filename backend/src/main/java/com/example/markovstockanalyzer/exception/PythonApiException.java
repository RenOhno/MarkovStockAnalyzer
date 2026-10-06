package com.example.markovstockanalyzer.exception;

public class PythonApiException extends RuntimeException {
    private final int statusCode;
    private final ApiErrorResponse error;

    public PythonApiException(int statusCode, ApiErrorResponse error, Throwable cause) {
        super(error.message(), cause);
        this.statusCode = statusCode;
        this.error = error;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public ApiErrorResponse getError() {
        return error;
    }
}
