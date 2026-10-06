package com.example.markovstockanalyzer.exception;

public class AnalysisResultNotFoundException extends RuntimeException {
    public AnalysisResultNotFoundException(Long analysisId) {
        super("Analysis result was not found: " + analysisId);
    }
}
