package com.example.markovstockanalyzer.exception;

public class PriceDatasetNotFoundException extends RuntimeException {
    public PriceDatasetNotFoundException(Long datasetId) {
        super("Price dataset was not found: " + datasetId);
    }
}
