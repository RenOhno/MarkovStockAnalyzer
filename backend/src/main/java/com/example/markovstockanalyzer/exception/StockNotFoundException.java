package com.example.markovstockanalyzer.exception;

public class StockNotFoundException extends RuntimeException {
    public StockNotFoundException(String stockId) {
        super("Stock was not found: " + stockId);
    }
}
