package com.example.markovstockanalyzer.dto.response;

import java.time.LocalDate;

public record SeriesPointResponse(
        LocalDate date,
        String close,
        String adjustedClose,
        Double returnValue,
        String state
) {
}
