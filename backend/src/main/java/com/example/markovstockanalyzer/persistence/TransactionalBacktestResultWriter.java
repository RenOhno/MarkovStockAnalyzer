package com.example.markovstockanalyzer.persistence;

import com.example.markovstockanalyzer.model.BacktestResult;
import com.example.markovstockanalyzer.repository.BacktestResultRepository;
import com.example.markovstockanalyzer.service.BacktestExecutionResult;
import com.example.markovstockanalyzer.service.BacktestResultWriter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("mysql")
public class TransactionalBacktestResultWriter extends BacktestResultWriter {
    public TransactionalBacktestResultWriter(BacktestResultRepository results) { super(results); }
    @Override @Transactional
    public BacktestResult save(BacktestExecutionResult execution) { return super.save(execution); }
}
