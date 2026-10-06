package com.example.markovstockanalyzer.exception;

public class InvalidBacktestRequestException extends RuntimeException {
    public InvalidBacktestRequestException(String message) {
        super(message);
    }
}
