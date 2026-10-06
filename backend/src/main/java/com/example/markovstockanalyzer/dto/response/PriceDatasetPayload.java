package com.example.markovstockanalyzer.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record PriceDatasetPayload(
        String ticker,
        String exchange,
        String timeZone,
        String priceBasis,
        String provider,
        String providerVersion,
        String adjustmentPolicy,
        OffsetDateTime fetchedAt,
        LocalDate coverageStart,
        LocalDate coverageEnd,
        String contentSha256,
        Map<String, Object> metadata,
        List<PricePoint> prices
) {
}
