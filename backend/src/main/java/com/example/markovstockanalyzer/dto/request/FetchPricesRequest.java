package com.example.markovstockanalyzer.dto.request;

import java.time.LocalDate;

public record FetchPricesRequest(
        String requestId,
        String ticker,
        String exchange,
        String timeZone,
        LocalDate startDate,
        LocalDate endDate,
        boolean includePreviousSession,
        String priceBasis,
        String provider
) {
}
