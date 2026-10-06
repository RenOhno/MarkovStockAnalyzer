package com.example.markovstockanalyzer.dto.request;

import com.example.markovstockanalyzer.dto.response.ConditionResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AnalysisCondition(
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal lowerThreshold,
        BigDecimal upperThreshold,
        Integer stateCount,
        String estimator,
        String windowMode,
        Integer windowSize,
        List<Integer> horizons
) {
    public static AnalysisCondition from(ConditionResponse condition) {
        return new AnalysisCondition(
                condition.startDate(), condition.endDate(),
                condition.lowerThreshold(), condition.upperThreshold(), condition.stateCount(),
                condition.estimator(), condition.windowMode(), condition.windowSize(), condition.horizons()
        );
    }
}
