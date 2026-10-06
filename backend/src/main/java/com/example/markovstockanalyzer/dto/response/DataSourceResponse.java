package com.example.markovstockanalyzer.dto.response;

import java.time.Instant;
import java.time.LocalDate;

public record DataSourceResponse(
        String provider,
        String providerVersion,
        String adjustmentPolicy,
        Instant fetchedAt,
        LocalDate coverageStart,
        LocalDate coverageEnd,
        String contentSha256,
        String calendarName,
        String calendarVersion
) {
}
