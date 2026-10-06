package com.example.markovstockanalyzer.exception;

public class InvalidHistoryTypeException extends RuntimeException {
    public InvalidHistoryTypeException() {
        super("History type must be ANALYSIS, BACKTEST or ALL");
    }
}
