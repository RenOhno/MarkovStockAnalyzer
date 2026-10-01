package com.example.markovstockanalyzer.exception;

public class ConditionNotFoundException extends RuntimeException {
    public ConditionNotFoundException(Long id) {
        super("Condition was not found: " + id);
    }
}
