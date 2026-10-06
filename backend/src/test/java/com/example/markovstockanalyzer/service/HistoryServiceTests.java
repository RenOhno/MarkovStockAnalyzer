package com.example.markovstockanalyzer.service;

import com.example.markovstockanalyzer.dto.response.CalculatedAnalysis;
import com.example.markovstockanalyzer.exception.InvalidHistoryTypeException;
import com.example.markovstockanalyzer.exception.InvalidPaginationException;
import com.example.markovstockanalyzer.model.*;
import com.example.markovstockanalyzer.repository.*;
import com.example.markovstockanalyzer.support.BacktestFixtures;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HistoryServiceTests {
    @Mock private AnalysisResultRepository analyses;
    @Mock private BacktestResultRepository backtests;
    @Mock private ConditionRepository conditions;
    @Mock private PriceDatasetRepository datasets;
    private HistoryService service;
    private static final Instant OLD = Instant.parse("2025-01-01T00:00:00Z");
    private static final Instant RECENT = Instant.parse("2026-10-06T01:00:00Z");

    @BeforeEach void setUp() { service = new HistoryService(analyses, backtests, conditions, datasets); }

    @Test void followsNumericDescendingIdAndUsesCreatedAtForEqualCrossTypeIds() {
        metadata();
        when(analyses.findAll()).thenReturn(List.of(analysis(2L, RECENT.plusSeconds(1)), analysis(7L, OLD), analysis(10L, OLD)));
        when(backtests.findAll()).thenReturn(List.of(backtest(7L, RECENT)));
        var page = service.findAll("ALL", null, 0, 20);
        assertEquals(List.of("ANALYSIS:10", "BACKTEST:7", "ANALYSIS:7", "ANALYSIS:2"),
                page.items().stream().map(row -> row.type() + ":" + row.id()).toList());
        assertThrows(UnsupportedOperationException.class, () -> page.items().clear());
    }

    @Test void usesStableTypeOrderWhenIdAndCreatedAtBothTie() {
        metadata();
        when(analyses.findAll()).thenReturn(List.of(analysis(7L, RECENT)));
        when(backtests.findAll()).thenReturn(List.of(backtest(7L, RECENT)));
        assertEquals(List.of("ANALYSIS", "BACKTEST"),
                service.findAll("ALL", null, 0, 20).items().stream().map(row -> row.type()).toList());
    }

    @ParameterizedTest @ValueSource(strings = {"ANALYSIS", "BACKTEST"})
    void onlyReadsSelectedResultRepository(String type) {
        metadata();
        if ("ANALYSIS".equals(type)) { when(analyses.findAll()).thenReturn(List.of(analysis(1L, OLD))); }
        else { when(backtests.findAll()).thenReturn(List.of(backtest(1L, OLD))); }
        assertEquals(1, service.findAll(type, null, 0, 20).totalElements());
        if ("ANALYSIS".equals(type)) { verifyNoInteractions(backtests); }
        else { verifyNoInteractions(analyses); }
    }

    @Test void stockFilterUsesConditionNotDatasetStockId() {
        metadata();
        when(analyses.findAll()).thenReturn(List.of(analysis(1L, OLD)));
        when(backtests.findAll()).thenReturn(List.of(backtest(1L, OLD)));
        var page = service.findAll("ALL", "7203", 0, 20);
        assertEquals(2, page.totalElements());
        assertTrue(page.items().stream().allMatch(row -> row.stockId().equals("7203")));
    }

    @Test void invalidTypeOrPagingStopsBeforeRepositoryReads() {
        assertThrows(InvalidHistoryTypeException.class, () -> service.findAll("OTHER", null, 0, 20));
        assertThrows(InvalidPaginationException.class, () -> service.findAll("ALL", null, -1, 20));
        assertThrows(InvalidPaginationException.class, () -> service.findAll("ALL", null, 0, 101));
        verifyNoInteractions(analyses, backtests, conditions, datasets);
    }

    private void metadata() {
        when(conditions.findById(101L)).thenReturn(Optional.of(BacktestFixtures.CONDITION));
        // History filtering explicitly uses Condition.stockId, even for an old inconsistent snapshot.
        when(datasets.findById(501L)).thenReturn(Optional.of(new PriceDataset(501L, "9001", BacktestFixtures.dataset(), OLD)));
    }

    private AnalysisResult analysis(Long id, Instant createdAt) {
        var calculated = new CalculatedAnalysis(List.of("UP", "FLAT", "DOWN"), BacktestFixtures.CONDITION.endDate(),
                "UP", 31, 30, List.of(List.of(15, 0, 0), List.of(0, 0, 0), List.of(0, 0, 15)),
                List.of(List.of(1.0, 0.0, 0.0), Arrays.asList(null, null, null), List.of(0.0, 0.0, 1.0)),
                "UNAVAILABLE", List.of(), List.of(), "msa-core-v0", Map.of());
        return new AnalysisResult(id, 101L, 501L, calculated, createdAt);
    }

    private BacktestResult backtest(Long id, Instant createdAt) {
        var request = BacktestFixtures.REQUEST;
        return new BacktestResult(id, 101L, 501L, request.testStart(), request.testEnd(), request.horizon(),
                request.minTrainStates(), request.trainingMode(), request.windowSize(), BacktestFixtures.mixed(), createdAt);
    }
}
