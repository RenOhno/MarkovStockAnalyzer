package com.example.markovstockanalyzer.service;

import com.example.markovstockanalyzer.dto.response.CalculatedAnalysis;
import com.example.markovstockanalyzer.dto.response.ConditionResponse;
import com.example.markovstockanalyzer.dto.response.PriceDatasetPayload;
import com.example.markovstockanalyzer.model.AnalysisResult;
import com.example.markovstockanalyzer.model.PriceDataset;
import com.example.markovstockanalyzer.repository.AnalysisResultRepository;
import com.example.markovstockanalyzer.repository.PriceDatasetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisResultWriterTests {
    private static final Long CONDITION_ID = 101L;
    private static final String STOCK_ID = "7203";
    private static final Long SAVED_DATASET_ID = 507L;
    private static final ConditionResponse CONDITION = new ConditionResponse(
            CONDITION_ID, "Saved condition", STOCK_ID,
            LocalDate.parse("2025-01-06"), LocalDate.parse("2025-02-19"),
            new BigDecimal("-0.005"), new BigDecimal("0.005"), 3,
            "MLE_STRICT", "FULL", null, List.of(1, 3, 5, 10), Instant.parse("2025-01-01T00:00:00Z")
    );

    @Mock
    private PriceDatasetRepository datasets;
    @Mock
    private AnalysisResultRepository results;
    @Mock
    private PriceDatasetPayload datasetPayload;
    @Mock
    private CalculatedAnalysis calculated;
    private AnalysisResultWriter writer;
    private AnalysisExecutionResult execution;
    private PriceDataset savedDataset;

    @BeforeEach
    void setUp() {
        writer = new AnalysisResultWriter(datasets, results);
        execution = new AnalysisExecutionResult(CONDITION, datasetPayload, calculated);
        savedDataset = new PriceDataset(SAVED_DATASET_ID, STOCK_ID, datasetPayload, Instant.now());
    }

    @Test
    void savesDatasetBeforeAnalysisWithCorrectIdsAndUnchangedCalculatedAnalysis() {
        AnalysisResult savedAnalysis = new AnalysisResult(1001L, CONDITION_ID, SAVED_DATASET_ID, calculated, Instant.now());
        when(datasets.save(STOCK_ID, datasetPayload)).thenReturn(savedDataset);
        when(results.save(CONDITION_ID, SAVED_DATASET_ID, calculated)).thenReturn(savedAnalysis);

        AnalysisResult result = writer.save(execution);

        InOrder order = inOrder(datasets, results);
        order.verify(datasets).save(eq(STOCK_ID), same(datasetPayload));
        order.verify(results).save(eq(CONDITION_ID), eq(SAVED_DATASET_ID), same(calculated));
        verifyNoMoreInteractions(datasets, results);
        assertSame(savedAnalysis, result);
        assertSame(calculated, result.calculatedAnalysis());
    }

    @Test
    void preservesReusedDatasetIdWithoutSavingAnotherDataset() {
        AnalysisResult savedAnalysis = new AnalysisResult(1001L, CONDITION_ID, SAVED_DATASET_ID, calculated, Instant.now());
        when(datasets.findById(SAVED_DATASET_ID)).thenReturn(Optional.of(savedDataset));
        when(results.save(CONDITION_ID, SAVED_DATASET_ID, calculated)).thenReturn(savedAnalysis);

        assertSame(savedAnalysis, writer.save(execution, SAVED_DATASET_ID));

        InOrder order = inOrder(datasets, results);
        order.verify(datasets).findById(SAVED_DATASET_ID);
        order.verify(results).save(eq(CONDITION_ID), eq(SAVED_DATASET_ID), same(calculated));
        verifyNoMoreInteractions(datasets, results);
    }

    @Test
    void datasetSaveFailureStopsBeforeAnalysisSaveAndPropagatesOriginalException() {
        IllegalStateException failure = new IllegalStateException("Dataset save failed");
        when(datasets.save(STOCK_ID, datasetPayload)).thenThrow(failure);

        assertSame(failure, assertThrows(IllegalStateException.class, () -> writer.save(execution)));

        verify(datasets).save(STOCK_ID, datasetPayload);
        verifyNoMoreInteractions(datasets);
        verifyNoInteractions(results);
    }

    @Test
    void analysisSaveFailurePropagatesAfterDatasetSave() {
        IllegalStateException failure = new IllegalStateException("Analysis save failed");
        when(datasets.save(STOCK_ID, datasetPayload)).thenReturn(savedDataset);
        when(results.save(CONDITION_ID, SAVED_DATASET_ID, calculated)).thenThrow(failure);

        assertSame(failure, assertThrows(IllegalStateException.class, () -> writer.save(execution)));

        InOrder order = inOrder(datasets, results);
        order.verify(datasets).save(STOCK_ID, datasetPayload);
        order.verify(results).save(CONDITION_ID, SAVED_DATASET_ID, calculated);
        verifyNoMoreInteractions(datasets, results);
    }
}
