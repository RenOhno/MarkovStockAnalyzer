package com.example.markovstockanalyzer.service;

import com.example.markovstockanalyzer.dto.response.ConditionResponse;
import com.example.markovstockanalyzer.dto.response.HistoryPageResponse;
import com.example.markovstockanalyzer.dto.response.HistorySummaryResponse;
import com.example.markovstockanalyzer.exception.ConditionNotFoundException;
import com.example.markovstockanalyzer.exception.InvalidHistoryTypeException;
import com.example.markovstockanalyzer.exception.PriceDatasetNotFoundException;
import com.example.markovstockanalyzer.repository.AnalysisResultRepository;
import com.example.markovstockanalyzer.repository.BacktestResultRepository;
import com.example.markovstockanalyzer.repository.ConditionRepository;
import com.example.markovstockanalyzer.repository.PriceDatasetRepository;
import com.example.markovstockanalyzer.validation.Pagination;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class HistoryService {
    private final AnalysisResultRepository analyses;
    private final BacktestResultRepository backtests;
    private final ConditionRepository conditions;
    private final PriceDatasetRepository datasets;

    public HistoryService(AnalysisResultRepository analyses, BacktestResultRepository backtests,
                          ConditionRepository conditions, PriceDatasetRepository datasets) {
        this.analyses = analyses;
        this.backtests = backtests;
        this.conditions = conditions;
        this.datasets = datasets;
    }

    public HistoryPageResponse findAll(String type, String stockId, int page, int size) {
        if (!"ALL".equals(type) && !"ANALYSIS".equals(type) && !"BACKTEST".equals(type)) {
            throw new InvalidHistoryTypeException();
        }
        Pagination.validate(page, size);
        List<HistorySummaryResponse> rows = new ArrayList<>();
        if (!"BACKTEST".equals(type)) {
            for (var result : analyses.findAll()) {
                ConditionResponse condition = condition(result.conditionId());
                if (stockId == null || stockId.equals(condition.stockId())) {
                    rows.add(summary("ANALYSIS", result.id(), condition, condition.startDate(), condition.endDate(),
                            result.datasetId(), result.calculatedAnalysis().predictionStatus(),
                            result.calculatedAnalysis().engineVersion(), result.createdAt()));
                }
            }
        }
        if (!"ANALYSIS".equals(type)) {
            for (var result : backtests.findAll()) {
                ConditionResponse condition = condition(result.conditionId());
                if (stockId == null || stockId.equals(condition.stockId())) {
                    rows.add(summary("BACKTEST", result.id(), condition, result.testStart(), result.testEnd(),
                            result.datasetId(), null, result.calculatedBacktest().engineVersion(), result.createdAt()));
                }
            }
        }
        // Design 17.1 specifies descending ID. IDs have separate per-type sequences,
        // so equal IDs use creation time descending, then type for a stable total order.
        rows.sort(Comparator.comparingLong((HistorySummaryResponse row) -> Long.parseLong(row.id())).reversed()
                .thenComparing(HistorySummaryResponse::createdAt, Comparator.reverseOrder())
                .thenComparing(HistorySummaryResponse::type));
        long offset = (long) page * size;
        int from = (int) Math.min(offset, rows.size());
        int to = (int) Math.min(offset + size, rows.size());
        return new HistoryPageResponse(List.copyOf(rows.subList(from, to)), page, size, rows.size());
    }

    private ConditionResponse condition(Long id) {
        return conditions.findById(id).orElseThrow(() -> new ConditionNotFoundException(id));
    }

    private HistorySummaryResponse summary(String type, Long id, ConditionResponse condition,
            LocalDate startDate, LocalDate endDate, Long datasetId, String predictionStatus, String engineVersion,
            Instant createdAt) {
        var dataset = datasets.findById(datasetId).orElseThrow(() -> new PriceDatasetNotFoundException(datasetId));
        return new HistorySummaryResponse(type, id.toString(), condition.id().toString(), condition.stockId(), condition.name(),
                startDate, endDate, datasetId.toString(), predictionStatus, engineVersion, createdAt,
                dataset.dataset().fetchedAt().toInstant());
    }
}
