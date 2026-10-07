package com.example.markovstockanalyzer.model;

import java.time.Instant;
import java.time.LocalDate;

public record DatasetCacheCriteria(String stockId, String provider, String priceBasis, String adjustmentPolicy,
                                   String calendarVersion, LocalDate startDate, LocalDate endDate,
                                   Instant fetchedSince, Instant now) {}
