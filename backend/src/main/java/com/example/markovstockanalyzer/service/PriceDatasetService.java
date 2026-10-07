package com.example.markovstockanalyzer.service;

import com.example.markovstockanalyzer.client.PythonAnalysisClient;
import com.example.markovstockanalyzer.dto.request.FetchPricesRequest;
import com.example.markovstockanalyzer.dto.response.ConditionResponse;
import com.example.markovstockanalyzer.exception.PriceDatasetNotFoundException;
import com.example.markovstockanalyzer.exception.StockNotFoundException;
import com.example.markovstockanalyzer.model.DatasetCacheCriteria;
import com.example.markovstockanalyzer.model.PriceDataset;
import com.example.markovstockanalyzer.repository.PriceDatasetRepository;
import com.example.markovstockanalyzer.repository.StockRepository;
import com.example.markovstockanalyzer.validation.ConditionDatasetValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;

/** HTTP happens between the adapter's short read/save transactions. */
@Service
@Profile("mysql")
public class PriceDatasetService {
    private final StockRepository stocks;
    private final PriceDatasetRepository datasets;
    private final PythonAnalysisClient python;
    private final String calendarVersion;
    public PriceDatasetService(StockRepository stocks, PriceDatasetRepository datasets, PythonAnalysisClient python,
                               @Value("${market-data.calendar-version}") String calendarVersion) {
        this.stocks = stocks; this.datasets = datasets; this.python = python; this.calendarVersion = calendarVersion;
    }
    public PriceDataset resolve(ConditionResponse condition, Long datasetId, String requestId) {
        if (datasetId != null) {
            var saved = datasets.findById(datasetId).orElseThrow(() -> new PriceDatasetNotFoundException(datasetId));
            ConditionDatasetValidator.validate(condition, saved);
            return saved;
        }
        Instant now = Instant.now();
        var criteria = new DatasetCacheCriteria(condition.stockId(), "YFINANCE", "PROVIDER_ADJUSTED_CLOSE",
                "PROVIDER_ADJUSTED_CLOSE_V1", calendarVersion, condition.startDate(), condition.endDate(),
                now.minus(Duration.ofHours(24)), now);
        var cached = datasets.findReusable(criteria);
        if (cached.isPresent()) { return cached.get(); }
        var stock = stocks.findAll().stream().filter(candidate -> condition.stockId().equals(candidate.id()))
                .findFirst().orElseThrow(() -> new StockNotFoundException(condition.stockId()));
        var request = new FetchPricesRequest(requestId, stock.ticker(), stock.exchange(), stock.timeZone(),
                condition.startDate(), condition.endDate(), true, criteria.priceBasis(), criteria.provider());
        var fetched = python.fetchPrices(request);
        return datasets.save(condition.stockId(), fetched);
    }
}
