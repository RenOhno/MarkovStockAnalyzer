package com.example.markovstockanalyzer.validation;

import com.example.markovstockanalyzer.dto.response.ConditionResponse;
import com.example.markovstockanalyzer.dto.response.PriceDatasetPayload;
import com.example.markovstockanalyzer.exception.DatasetConditionMismatchException;
import com.example.markovstockanalyzer.model.PriceDataset;

public final class ConditionDatasetValidator {
    private ConditionDatasetValidator() {
    }

    public static void validate(ConditionResponse condition, PriceDataset saved) {
        if (!condition.stockId().equals(saved.stockId())) {
            throw new DatasetConditionMismatchException("Dataset stockId does not match condition");
        }
        PriceDatasetPayload dataset = saved.dataset();
        if (dataset == null || !"PROVIDER_ADJUSTED_CLOSE".equals(dataset.priceBasis())) {
            throw new DatasetConditionMismatchException("Dataset priceBasis does not match condition");
        }
        // Design 10.2 isolates the XTKS calendar in Python. These are necessary
        // overlap/preceding-price checks; Python checks all exact required sessions.
        if (dataset.coverageStart() == null || dataset.coverageEnd() == null
                || !dataset.coverageStart().isBefore(condition.startDate())
                || dataset.coverageEnd().isBefore(condition.startDate())
                || dataset.prices() == null
                || dataset.prices().stream().noneMatch(price -> price != null && price.date() != null
                        && price.date().isBefore(condition.startDate()))
                || dataset.prices().stream().noneMatch(price -> price != null && price.date() != null
                        && !price.date().isBefore(condition.startDate())
                        && !price.date().isAfter(condition.endDate()))) {
            throw new DatasetConditionMismatchException("Dataset must include the condition range and preceding price");
        }
    }
}
