package com.example.markovstockanalyzer.exception;

public class CalculationInvariantFailedException extends RuntimeException {
    public CalculationInvariantFailedException(String message) {
        super(message);
    }

    public String getCode() {
        return "CALCULATION_INVARIANT_FAILED";
    }
}
