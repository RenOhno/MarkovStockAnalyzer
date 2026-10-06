package com.example.markovstockanalyzer.dto.response;

import java.util.List;

public record CalculatedSeries(String priceBasis, List<SeriesPoint> points, String engineVersion) {
}
