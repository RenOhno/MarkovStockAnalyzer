package com.example.markovstockanalyzer.dto.response;

import java.util.List;

public record ForecastPayload(Integer horizon, List<Double> probabilities) {
}
