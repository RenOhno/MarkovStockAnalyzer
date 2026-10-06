package com.example.markovstockanalyzer.exception;

public class AnalysisServiceUnavailableException extends RuntimeException {
    public AnalysisServiceUnavailableException(Throwable cause) {
        super("Python analysis service is unavailable", cause);
    }
}
