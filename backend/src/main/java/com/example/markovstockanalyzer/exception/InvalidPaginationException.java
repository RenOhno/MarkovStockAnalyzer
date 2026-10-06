package com.example.markovstockanalyzer.exception;

public class InvalidPaginationException extends RuntimeException {
    public InvalidPaginationException() {
        super("page must be nonnegative and size must be between 1 and 100");
    }
}
