package com.example.markovstockanalyzer.dto.response;

import java.util.List;

public record AnalysisWarning(String code, List<String> states) {
}
