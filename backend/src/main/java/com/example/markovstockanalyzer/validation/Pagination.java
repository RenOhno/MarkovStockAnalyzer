package com.example.markovstockanalyzer.validation;

import com.example.markovstockanalyzer.exception.InvalidPaginationException;

public final class Pagination {
    private Pagination() {
    }

    public static void validate(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidPaginationException();
        }
    }

    public static int parse(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new InvalidPaginationException();
        }
    }
}
