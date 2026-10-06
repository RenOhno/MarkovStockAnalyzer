package com.example.markovstockanalyzer.persistence;

import com.example.markovstockanalyzer.persistence.repository.StockJpaRepository;
import com.example.markovstockanalyzer.repository.*;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PersistenceProfileTests {
    @Autowired private ApplicationContext context;

    @Test void defaultApiKeepsInMemoryRepositoriesAndDoesNotRequireLocalMysql() {
        assertTrue(context.getBeansOfType(DataSource.class).isEmpty());
        assertTrue(context.getBeansOfType(EntityManagerFactory.class).isEmpty());
        assertTrue(context.getBeansOfType(Flyway.class).isEmpty());
        assertTrue(context.getBeansOfType(StockJpaRepository.class).isEmpty());
        assertInstanceOf(InMemoryStockRepository.class, context.getBean(StockRepository.class));
        assertInstanceOf(InMemoryAnalysisResultRepository.class, context.getBean(AnalysisResultRepository.class));
        assertInstanceOf(InMemoryBacktestResultRepository.class, context.getBean(BacktestResultRepository.class));
    }
}
