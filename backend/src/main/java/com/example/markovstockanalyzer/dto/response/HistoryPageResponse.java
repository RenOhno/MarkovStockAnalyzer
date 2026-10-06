package com.example.markovstockanalyzer.dto.response;

import java.util.List;

public record HistoryPageResponse(List<HistorySummaryResponse> items, int page, int size, long totalElements) {
}
