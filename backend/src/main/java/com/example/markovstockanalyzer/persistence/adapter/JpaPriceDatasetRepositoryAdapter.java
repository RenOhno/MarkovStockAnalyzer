package com.example.markovstockanalyzer.persistence.adapter;

import com.example.markovstockanalyzer.dto.response.*;
import com.example.markovstockanalyzer.exception.CalculationInvariantFailedException;
import com.example.markovstockanalyzer.model.*;
import com.example.markovstockanalyzer.persistence.entity.*;
import com.example.markovstockanalyzer.persistence.repository.*;
import com.example.markovstockanalyzer.repository.PriceDatasetRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

@Repository
@Profile("mysql")
@Transactional(readOnly = true)
public class JpaPriceDatasetRepositoryAdapter implements PriceDatasetRepository {
    private final PriceDatasetJpaRepository datasets;
    private final StockJpaRepository stocks;
    private final PersistenceJson json;
    public JpaPriceDatasetRepositoryAdapter(PriceDatasetJpaRepository datasets, StockJpaRepository stocks, PersistenceJson json) {
        this.datasets = datasets; this.stocks = stocks; this.json = json;
    }
    @Transactional
    public PriceDataset save(String stockId, PriceDatasetPayload payload) {
        validatePrices(payload);
        var entity = new PriceDatasetEntity();
        entity.setStock(stocks.getReferenceById(Long.valueOf(stockId)));
        entity.setProvider(payload.provider()); entity.setProviderVersion(payload.providerVersion());
        entity.setPriceBasis(payload.priceBasis()); entity.setAdjustmentPolicy(payload.adjustmentPolicy());
        entity.setFetchedAt(payload.fetchedAt().toInstant().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        entity.setCoverageStart(payload.coverageStart()); entity.setCoverageEnd(payload.coverageEnd());
        entity.setRowCount(payload.prices().size()); entity.setContentSha256(payload.contentSha256());
        entity.setMetadata(json.write(payload.metadata()));
        for (var point : payload.prices()) {
            var price = new StockPriceEntity(); price.setId(new StockPriceId(null, point.date()));
            price.setClose(decimal(point.close())); price.setAdjustedClose(decimal(point.adjustedClose())); price.setVolume(point.volume());
            entity.addPrice(price);
        }
        return domain(datasets.saveAndFlush(entity));
    }
    public Optional<PriceDataset> findById(Long id) { return datasets.findById(id).map(this::domain); }
    public Optional<PriceDataset> findReusable(DatasetCacheCriteria criteria) {
        if (criteria.calendarVersion() == null || criteria.calendarVersion().isBlank()) { return Optional.empty(); }
        return datasets.cacheCandidates(Long.valueOf(criteria.stockId()), criteria.provider(), criteria.priceBasis(),
                        criteria.adjustmentPolicy(), criteria.fetchedSince(), criteria.now(), criteria.startDate(), criteria.endDate()).stream()
                .filter(entity -> covers(entity, criteria)).findFirst().map(this::domain);
    }
    private boolean covers(PriceDatasetEntity entity, DatasetCacheCriteria criteria) {
        Map<String, Object> metadata = json.read(entity.getMetadata(), new TypeReference<Map<String, Object>>() {});
        if (!criteria.calendarVersion().equals(metadata.get("calendarVersion"))) { return false; }
        // The query requires a successfully validated analysis/backtest covering the requested
        // range. Python checked its exact XTKS sessions and preceding session. This also handles
        // holiday end dates without approximating a market calendar in Java.
        return entity.getCoverageStart().isBefore(criteria.startDate())
                && !entity.getCoverageEnd().isBefore(criteria.startDate())
                && entity.getPrices().stream().anyMatch(point -> point.getId().getTradeDate().isBefore(criteria.startDate()))
                && entity.getPrices().stream().anyMatch(point -> !point.getId().getTradeDate().isBefore(criteria.startDate())
                        && !point.getId().getTradeDate().isAfter(criteria.endDate()));
    }
    private PriceDataset domain(PriceDatasetEntity entity) {
        Map<String, Object> metadata = json.read(entity.getMetadata(), new TypeReference<Map<String, Object>>() {});
        var prices = entity.getPrices().stream().sorted(Comparator.comparing(point -> point.getId().getTradeDate()))
                .map(point -> new PricePoint(point.getId().getTradeDate(), point.getClose().toPlainString(),
                        point.getAdjustedClose().toPlainString(), point.getVolume())).toList();
        if (entity.getRowCount() != prices.size()) { throw new CalculationInvariantFailedException("Stored dataset row count is invalid"); }
        var payload = new PriceDatasetPayload((String) metadata.get("ticker"), (String) metadata.get("exchange"),
                (String) metadata.get("timeZone"), entity.getPriceBasis(), entity.getProvider(), entity.getProviderVersion(),
                entity.getAdjustmentPolicy(), entity.getFetchedAt().atOffset(ZoneOffset.UTC), entity.getCoverageStart(),
                entity.getCoverageEnd(), entity.getContentSha256(), metadata, prices);
        validatePrices(payload);
        return new PriceDataset(entity.getId(), entity.getStock().getId().toString(), payload, entity.getFetchedAt());
    }
    private void validatePrices(PriceDatasetPayload payload) {
        if (payload == null || payload.prices() == null || payload.prices().isEmpty() || payload.metadata() == null
                || payload.coverageStart() == null || payload.coverageEnd() == null) { invalid(); }
        LocalDate previous = null;
        for (PricePoint point : payload.prices()) {
            if (point == null || point.date() == null || (previous != null && !point.date().isAfter(previous))
                    || (point.volume() != null && point.volume() < 0)) { invalid(); }
            decimal(point.close()); decimal(point.adjustedClose()); previous = point.date();
        }
        if (!payload.coverageStart().equals(payload.prices().getFirst().date())
                || !payload.coverageEnd().equals(payload.prices().getLast().date())) { invalid(); }
        for (String key : List.of("ticker", "exchange", "timeZone")) {
            if (!(payload.metadata().get(key) instanceof String value) || value.isBlank()) { invalid(); }
        }
        if (!Objects.equals(payload.ticker(), payload.metadata().get("ticker"))
                || !Objects.equals(payload.exchange(), payload.metadata().get("exchange"))
                || !Objects.equals(payload.timeZone(), payload.metadata().get("timeZone"))) { invalid(); }
    }
    private BigDecimal decimal(String value) {
        try {
            var decimal = new BigDecimal(value).setScale(10, RoundingMode.UNNECESSARY);
            if (decimal.signum() <= 0 || decimal.precision() > 24) { invalid(); }
            return decimal;
        } catch (NumberFormatException | ArithmeticException | NullPointerException exception) { invalid(); return null; }
    }
    private void invalid() { throw new CalculationInvariantFailedException("Price dataset coverage or rows are invalid"); }
}
