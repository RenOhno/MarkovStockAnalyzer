package com.example.markovstockanalyzer.dto.response;

import java.util.List;

public record AnalysisSeriesResponse(String analysisId, String priceBasis, List<SeriesPointResponse> points) {
}
