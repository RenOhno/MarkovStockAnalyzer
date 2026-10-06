package com.example.markovstockanalyzer.dto.response;

import java.time.LocalDate;

public record SeriesPoint(
        LocalDate date,
        String close,
        String adjustedClose,
        Double returnValue,
        String state
) {
}
