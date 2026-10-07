package com.example.markovstockanalyzer.persistence.adapter;

import com.example.markovstockanalyzer.dto.response.StockSummary;
import com.example.markovstockanalyzer.repository.StockRepository;
import com.example.markovstockanalyzer.persistence.repository.StockJpaRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
@Profile("mysql")
@Transactional(readOnly = true)
public class JpaStockRepositoryAdapter implements StockRepository {
    private final StockJpaRepository stocks;
    public JpaStockRepositoryAdapter(StockJpaRepository stocks) { this.stocks = stocks; }
    public List<StockSummary> findAll() {
        return stocks.findAll().stream().filter(stock -> Boolean.TRUE.equals(stock.getEnabled()))
                .map(stock -> new StockSummary(stock.getId().toString(), stock.getTicker(), stock.getName(),
                        stock.getExchange(), stock.getCurrency(), stock.getTimeZone())).toList();
    }
    public boolean existsById(String id) {
        try { return stocks.findById(Long.valueOf(id)).filter(stock -> Boolean.TRUE.equals(stock.getEnabled())).isPresent(); }
        catch (NumberFormatException exception) { return false; }
    }
}
