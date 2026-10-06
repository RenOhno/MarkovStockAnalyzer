package com.example.markovstockanalyzer.mapper;

import com.example.markovstockanalyzer.dto.response.AnalysisResultResponse;
import com.example.markovstockanalyzer.dto.response.CalculatedAnalysis;
import com.example.markovstockanalyzer.dto.response.DataSourceResponse;
import com.example.markovstockanalyzer.dto.response.PriceDatasetPayload;
import com.example.markovstockanalyzer.dto.response.ProvenanceResponse;
import com.example.markovstockanalyzer.model.AnalysisResult;
import com.example.markovstockanalyzer.model.PriceDataset;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AnalysisResultResponseMapper {
    public AnalysisResultResponse map(AnalysisResult result, PriceDataset dataset) {
        if (!result.datasetId().equals(dataset.id())) {
            throw new IllegalArgumentException("PriceDataset id does not match analysis datasetId");
        }
        CalculatedAnalysis calculated = result.calculatedAnalysis();
        PriceDatasetPayload payload = dataset.dataset();
        DataSourceResponse dataSource = dataSource(dataset);
        ProvenanceResponse provenance = provenance(calculated.runtime());
        return new AnalysisResultResponse(
                result.id().toString(), result.conditionId().toString(), result.datasetId().toString(),
                calculated.stateOrder(), payload.priceBasis(), calculated.asOfDate(), calculated.currentState(),
                calculated.sampleCount(), calculated.transitionCount(), calculated.transitionCounts(),
                calculated.transitionMatrix(), calculated.predictionStatus(), calculated.forecasts(),
                calculated.warnings(), dataSource, provenance, calculated.engineVersion(), result.createdAt()
        );
    }

    public DataSourceResponse dataSource(PriceDataset dataset) {
        PriceDatasetPayload payload = dataset.dataset();
        return new DataSourceResponse(
                payload.provider(), payload.providerVersion(), payload.adjustmentPolicy(),
                payload.fetchedAt().toInstant(), payload.coverageStart(), payload.coverageEnd(),
                payload.contentSha256(), label(value(payload.metadata(), "calendarName")),
                version(value(payload.metadata(), "calendarVersion"))
        );
    }

    public ProvenanceResponse provenance(Map<String, Object> runtime) {
        return new ProvenanceResponse(
                label(value(runtime, "engineVersion")), commit(value(runtime, "gitCommit")),
                dependencyVersions(runtime), label(value(runtime, "normalizationVersion")),
                label(value(runtime, "configurationVersion"))
        );
    }

    private Map<String, String> dependencyVersions(Map<String, Object> runtime) {
        Map<?, ?> nested = value(runtime, "dependencyVersions") instanceof Map<?, ?> map ? map : Map.of();
        Map<String, String> versions = new LinkedHashMap<>();
        // Only the dependencies already reported by Python are public. Never copy an
        // arbitrary runtime map, which may contain tokens, paths or other diagnostics.
        for (String dependency : List.of("python", "numpy", "pandas")) {
            String version = version(nested.get(dependency));
            if (version == null) {
                version = version(value(runtime, dependency + "Version"));
            }
            if (version != null) {
                versions.put(dependency, version);
            }
        }
        return versions.isEmpty() ? null : Map.copyOf(versions);
    }

    private Object value(Map<String, Object> values, String key) {
        return values == null ? null : values.get(key);
    }

    // Typed, bounded formats prevent nested diagnostic objects and path/URL strings
    // from leaking even under an otherwise permitted provenance key.
    private String label(Object value) {
        return value instanceof String text && text.matches("[A-Za-z0-9][A-Za-z0-9._+-]{0,127}") ? text : null;
    }

    private String version(Object value) {
        return value instanceof String text && text.matches("[0-9][A-Za-z0-9._+-]{0,127}") ? text : null;
    }

    private String commit(Object value) {
        return value instanceof String text && text.matches("[0-9a-fA-F]{7,64}") ? text : null;
    }
}
