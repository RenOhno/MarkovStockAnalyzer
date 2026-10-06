package com.example.markovstockanalyzer.exception;

public class BacktestResultNotFoundException extends RuntimeException {
    public BacktestResultNotFoundException(Long id) {
        super("Backtest result was not found: " + id);
    }
}
