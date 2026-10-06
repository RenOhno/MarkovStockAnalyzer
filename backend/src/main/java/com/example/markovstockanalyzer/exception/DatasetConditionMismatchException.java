package com.example.markovstockanalyzer.exception;

public class DatasetConditionMismatchException extends RuntimeException {
    public DatasetConditionMismatchException(String message) {
        super(message);
    }

    public String getCode() {
        return "DATASET_CONDITION_MISMATCH";
    }
}
