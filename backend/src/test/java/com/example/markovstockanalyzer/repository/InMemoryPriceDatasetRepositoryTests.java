package com.example.markovstockanalyzer.repository;

import com.example.markovstockanalyzer.dto.response.PriceDatasetPayload;
import com.example.markovstockanalyzer.dto.response.PricePoint;
import com.example.markovstockanalyzer.model.PriceDataset;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryPriceDatasetRepositoryTests {
    private final PriceDatasetRepository repository = new InMemoryPriceDatasetRepository();

    @Test
    void assignsIdAndRetrievesDatasetWithStockAndCompletePayload() {
        PriceDatasetPayload payload = dataset(Map.of("schemaVersion", 1), List.of(point()));

        PriceDataset saved = repository.save("7203", payload);

        assertTrue(saved.id() > 0);
        assertEquals("7203", saved.stockId());
        assertEquals(payload, saved.dataset());
        assertNotNull(saved.createdAt());
        assertEquals(saved, repository.findById(saved.id()).orElseThrow());
    }

    @Test
    void eachSaveGetsNewIdWithoutOverwritingSameContent() {
        PriceDatasetPayload payload = dataset(Map.of(), List.of(point()));

        PriceDataset first = repository.save("7203", payload);
        PriceDataset second = repository.save("7203", payload);

        assertNotEquals(first.id(), second.id());
        assertEquals(first, repository.findById(first.id()).orElseThrow());
        assertEquals(second, repository.findById(second.id()).orElseThrow());
    }

    @Test
    void returnsEmptyForUnknownId() {
        assertTrue(repository.findById(999L).isEmpty());
    }

    @Test
    void protectsSavedPricesAndNestedMetadataFromMutation() {
        List<PricePoint> prices = new ArrayList<>(List.of(point()));
        Map<String, Object> fetchOptions = new LinkedHashMap<>(Map.of("auto_adjust", false));
        List<String> flags = new ArrayList<>(List.of("SYNTHETIC_FIXTURE"));
        Map<String, Object> metadata = new LinkedHashMap<>(Map.of(
                "schemaVersion", 1, "fetchOptions", fetchOptions, "qualityFlags", flags));
        metadata.put("optional", null);
        PriceDataset saved = repository.save("7203", dataset(metadata, prices));

        prices.clear();
        fetchOptions.put("auto_adjust", true);
        flags.clear();
        metadata.clear();

        PriceDatasetPayload stored = repository.findById(saved.id()).orElseThrow().dataset();
        assertEquals(List.of(point()), stored.prices());
        assertEquals(Map.of("auto_adjust", false), stored.metadata().get("fetchOptions"));
        assertEquals(List.of("SYNTHETIC_FIXTURE"), stored.metadata().get("qualityFlags"));
        assertTrue(stored.metadata().containsKey("optional"));
        assertNull(stored.metadata().get("optional"));
        assertThrows(UnsupportedOperationException.class, () -> stored.prices().clear());
        assertThrows(UnsupportedOperationException.class, () -> stored.metadata().clear());
        assertThrows(UnsupportedOperationException.class, () -> ((Map<?, ?>) stored.metadata().get("fetchOptions")).clear());
        assertThrows(UnsupportedOperationException.class, () -> ((List<?>) stored.metadata().get("qualityFlags")).clear());
    }

    private PricePoint point() {
        return new PricePoint(LocalDate.parse("2025-01-06"), "100.0000000000", "99.1234567890", null);
    }

    private PriceDatasetPayload dataset(Map<String, Object> metadata, List<PricePoint> prices) {
        return new PriceDatasetPayload(
                "7203.T", "XTKS", "Asia/Tokyo", "PROVIDER_ADJUSTED_CLOSE", "YFINANCE", "0.2.65",
                "PROVIDER_ADJUSTED_CLOSE_V1", OffsetDateTime.parse("2026-10-06T01:02:03Z"),
                LocalDate.parse("2025-01-06"), LocalDate.parse("2025-01-06"), "a".repeat(64), metadata, prices
        );
    }
}
