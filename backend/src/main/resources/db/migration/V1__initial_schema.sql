CREATE TABLE stocks (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  ticker VARCHAR(32) NOT NULL,
  name VARCHAR(120) NOT NULL,
  exchange VARCHAR(16) NOT NULL,
  currency CHAR(3) NOT NULL,
  time_zone VARCHAR(64) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_stocks_exchange_ticker (exchange, ticker),
  CONSTRAINT ck_stock_enabled CHECK (enabled IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE price_datasets (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  stock_id BIGINT UNSIGNED NOT NULL,
  provider VARCHAR(32) NOT NULL,
  provider_version VARCHAR(64) NOT NULL,
  price_basis VARCHAR(32) NOT NULL,
  adjustment_policy VARCHAR(64) NOT NULL,
  fetched_at DATETIME(6) NOT NULL,
  coverage_start DATE NOT NULL,
  coverage_end DATE NOT NULL,
  row_count INT UNSIGNED NOT NULL,
  content_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  metadata JSON NOT NULL,
  PRIMARY KEY (id),
  KEY ix_dataset_lookup (stock_id, provider, price_basis, adjustment_policy, fetched_at),
  CONSTRAINT fk_dataset_stock FOREIGN KEY (stock_id) REFERENCES stocks(id),
  CONSTRAINT ck_dataset_basis CHECK (price_basis = 'PROVIDER_ADJUSTED_CLOSE'),
  CONSTRAINT ck_dataset_dates CHECK (coverage_start <= coverage_end),
  CONSTRAINT ck_dataset_rows CHECK (row_count >= 2)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE stock_prices (
  dataset_id BIGINT UNSIGNED NOT NULL,
  trade_date DATE NOT NULL,
  close DECIMAL(24,10) NOT NULL,
  adjusted_close DECIMAL(24,10) NOT NULL,
  volume BIGINT UNSIGNED NULL,
  PRIMARY KEY (dataset_id, trade_date),
  CONSTRAINT fk_price_dataset FOREIGN KEY (dataset_id) REFERENCES price_datasets(id),
  CONSTRAINT ck_price_positive CHECK (close > 0 AND adjusted_close > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE analysis_conditions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  stock_id BIGINT UNSIGNED NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  state_count TINYINT UNSIGNED NOT NULL DEFAULT 3,
  lower_threshold DECIMAL(12,10) NOT NULL,
  upper_threshold DECIMAL(12,10) NOT NULL,
  estimator VARCHAR(32) NOT NULL,
  window_mode VARCHAR(16) NOT NULL,
  window_size SMALLINT UNSIGNED NULL,
  horizons JSON NOT NULL,
  schema_version SMALLINT UNSIGNED NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY ix_condition_stock_created (stock_id, created_at, id),
  CONSTRAINT fk_condition_stock FOREIGN KEY (stock_id) REFERENCES stocks(id),
  CONSTRAINT ck_condition_dates CHECK (start_date <= end_date),
  CONSTRAINT ck_condition_states CHECK (state_count = 3),
  CONSTRAINT ck_condition_thresholds CHECK (
    lower_threshold > -1 AND lower_threshold <= 0 AND
    upper_threshold >= 0 AND upper_threshold < 1 AND
    lower_threshold < upper_threshold
  ),
  CONSTRAINT ck_condition_estimator CHECK (estimator = 'MLE_STRICT'),
  CONSTRAINT ck_condition_window CHECK (window_mode = 'FULL' AND window_size IS NULL),
  CONSTRAINT ck_condition_horizons CHECK (
    JSON_TYPE(horizons) = 'ARRAY' AND JSON_LENGTH(horizons) = 4
  ),
  CONSTRAINT ck_condition_version CHECK (schema_version >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE analysis_results (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  condition_id BIGINT UNSIGNED NOT NULL,
  dataset_id BIGINT UNSIGNED NOT NULL,
  as_of_date DATE NOT NULL,
  current_state CHAR(4) NOT NULL,
  sample_count INT UNSIGNED NOT NULL,
  transition_count INT UNSIGNED NOT NULL,
  prediction_status VARCHAR(16) NOT NULL,
  forecasts JSON NOT NULL,
  quality JSON NOT NULL,
  engine_version VARCHAR(64) NOT NULL,
  runtime JSON NOT NULL,
  result_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY ix_analysis_condition_created (condition_id, created_at, id),
  KEY ix_analysis_created (created_at, id),
  KEY ix_analysis_dataset (dataset_id),
  CONSTRAINT fk_analysis_condition FOREIGN KEY (condition_id) REFERENCES analysis_conditions(id),
  CONSTRAINT fk_analysis_dataset FOREIGN KEY (dataset_id) REFERENCES price_datasets(id),
  CONSTRAINT ck_analysis_state CHECK (current_state IN ('UP','FLAT','DOWN')),
  CONSTRAINT ck_analysis_count CHECK (sample_count >= 30 AND transition_count + 1 = sample_count),
  CONSTRAINT ck_analysis_status CHECK (prediction_status IN ('AVAILABLE','UNAVAILABLE')),
  CONSTRAINT ck_analysis_forecasts CHECK (JSON_TYPE(forecasts) = 'ARRAY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE transition_probabilities (
  analysis_id BIGINT UNSIGNED NOT NULL,
  from_state CHAR(4) NOT NULL,
  to_state CHAR(4) NOT NULL,
  transition_count INT UNSIGNED NOT NULL,
  probability DOUBLE NULL,
  PRIMARY KEY (analysis_id, from_state, to_state),
  CONSTRAINT fk_transition_analysis FOREIGN KEY (analysis_id) REFERENCES analysis_results(id),
  CONSTRAINT ck_transition_from CHECK (from_state IN ('UP','FLAT','DOWN')),
  CONSTRAINT ck_transition_to CHECK (to_state IN ('UP','FLAT','DOWN')),
  CONSTRAINT ck_transition_probability CHECK (
    probability IS NULL OR (probability >= 0 AND probability <= 1)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE backtest_results (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  condition_id BIGINT UNSIGNED NOT NULL,
  dataset_id BIGINT UNSIGNED NOT NULL,
  test_start DATE NOT NULL,
  test_end DATE NOT NULL,
  horizon SMALLINT UNSIGNED NOT NULL DEFAULT 1,
  min_train_states SMALLINT UNSIGNED NOT NULL,
  training_mode VARCHAR(16) NOT NULL,
  window_size SMALLINT UNSIGNED NULL,
  eligible_count INT UNSIGNED NOT NULL,
  predicted_count INT UNSIGNED NOT NULL,
  correct_count INT UNSIGNED NOT NULL,
  skipped_count INT UNSIGNED NOT NULL,
  metrics JSON NOT NULL,
  engine_version VARCHAR(64) NOT NULL,
  runtime JSON NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY ix_backtest_condition_created (condition_id, created_at, id),
  KEY ix_backtest_created (created_at, id),
  KEY ix_backtest_dataset (dataset_id),
  CONSTRAINT fk_backtest_condition FOREIGN KEY (condition_id) REFERENCES analysis_conditions(id),
  CONSTRAINT fk_backtest_dataset FOREIGN KEY (dataset_id) REFERENCES price_datasets(id),
  CONSTRAINT ck_backtest_dates CHECK (test_start <= test_end),
  CONSTRAINT ck_backtest_horizon CHECK (horizon = 1),
  CONSTRAINT ck_backtest_training CHECK (min_train_states >= 30),
  CONSTRAINT ck_backtest_mode CHECK (training_mode = 'EXPANDING' AND window_size IS NULL),
  CONSTRAINT ck_backtest_counts CHECK (
    eligible_count BETWEEN 1 AND 1000 AND
    predicted_count + skipped_count = eligible_count AND
    correct_count <= predicted_count
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE backtest_predictions (
  backtest_id BIGINT UNSIGNED NOT NULL,
  target_date DATE NOT NULL,
  origin_date DATE NOT NULL,
  train_start DATE NOT NULL,
  train_end DATE NOT NULL,
  actual_state CHAR(4) NOT NULL,
  predicted_state CHAR(4) NULL,
  probabilities JSON NULL,
  majority_state CHAR(4) NULL,
  persistence_state CHAR(4) NULL,
  status VARCHAR(16) NOT NULL,
  skip_code VARCHAR(64) NULL,
  PRIMARY KEY (backtest_id, target_date),
  CONSTRAINT fk_prediction_backtest FOREIGN KEY (backtest_id) REFERENCES backtest_results(id),
  CONSTRAINT ck_prediction_dates CHECK (
    train_start <= train_end AND train_end = origin_date AND origin_date < target_date
  ),
  CONSTRAINT ck_prediction_actual CHECK (actual_state IN ('UP','FLAT','DOWN')),
  CONSTRAINT ck_prediction_predicted CHECK (
    predicted_state IS NULL OR predicted_state IN ('UP','FLAT','DOWN')
  ),
  CONSTRAINT ck_prediction_majority CHECK (
    majority_state IS NULL OR majority_state IN ('UP','FLAT','DOWN')
  ),
  CONSTRAINT ck_prediction_persistence CHECK (
    persistence_state IS NULL OR persistence_state IN ('UP','FLAT','DOWN')
  ),
  CONSTRAINT ck_prediction_status CHECK (status IN ('SCORED','SKIPPED')),
  CONSTRAINT ck_prediction_fields CHECK (
    (status = 'SCORED' AND predicted_state IS NOT NULL AND probabilities IS NOT NULL
      AND majority_state IS NOT NULL AND persistence_state IS NOT NULL AND skip_code IS NULL)
    OR
    (status = 'SKIPPED' AND predicted_state IS NULL AND probabilities IS NULL
      AND majority_state IS NULL AND persistence_state IS NULL AND skip_code IS NOT NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
