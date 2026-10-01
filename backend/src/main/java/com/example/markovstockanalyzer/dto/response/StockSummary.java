package com.example.markovstockanalyzer.dto.response;

public record StockSummary(
        String id,
        String ticker,
        String name,
        String exchange,
        String currency,
        String timeZone
) {
}
