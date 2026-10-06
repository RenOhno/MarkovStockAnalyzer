package com.example.markovstockanalyzer.dto.response;

import java.time.LocalDate;

public record PricePoint(
        LocalDate date,
        String close,
        String adjustedClose,
        Long volume
) {
}
