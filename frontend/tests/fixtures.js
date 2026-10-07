export const stocks = [{ id: '7203', ticker: '7203.T', name: 'Toyota Motor', exchange: 'XTKS', currency: 'JPY', timeZone: 'Asia/Tokyo' }];
export const condition = { id: 101, name: '研究用条件', stockId: '7203', startDate: '2025-01-06', endDate: '2025-02-28', lowerThreshold: -0.005,
  upperThreshold: 0.005, stateCount: 3, estimator: 'MLE_STRICT', windowMode: 'FULL', windowSize: null, horizons: [1, 3, 5, 10], createdAt: '2025-01-01T00:00:00Z' };
export const dataSource = { provider: 'YFINANCE', providerVersion: 'fixture', adjustmentPolicy: 'PROVIDER_ADJUSTED_CLOSE_V1', fetchedAt: '2025-03-01T00:00:00Z',
  coverageStart: '2024-12-30', coverageEnd: '2025-02-28', contentSha256: 'a'.repeat(64), calendarName: 'XTKS', calendarVersion: 'fixture' };
export const provenance = { engineVersion: 'msa-core-v1', gitCommit: null, dependencyVersions: { numpy: 'fixture' }, normalizationVersion: 'NORMALIZATION_V1', configurationVersion: null };
export const analysis = { id: '1001', conditionId: '101', datasetId: '501', stateOrder: ['UP', 'FLAT', 'DOWN'], priceBasis: 'PROVIDER_ADJUSTED_CLOSE',
  asOfDate: '2025-02-28', currentState: 'UP', sampleCount: 31, transitionCount: 30, transitionCounts: [[10, 0, 0], [0, 10, 0], [0, 0, 10]],
  transitionMatrix: [[1, 0, 0], [0, 1, 0], [0, 0, 1]], predictionStatus: 'AVAILABLE',
  forecasts: [1, 3, 5, 10].map(horizon => ({ horizon, probabilities: [1, 0, 0] })), warnings: [],
  dataSource, provenance, engineVersion: 'msa-core-v1', createdAt: '2025-03-01T00:00:01Z' };
export const unavailable = { ...analysis, predictionStatus: 'UNAVAILABLE', transitionCounts: [[30, 0, 0], [0, 0, 0], [0, 0, 0]],
  transitionMatrix: [[1, 0, 0], [null, null, null], [null, null, null]], forecasts: [], warnings: [{ code: 'ZERO_ROW_UNESTIMATED', states: ['FLAT', 'DOWN'] }] };
export const series = { analysisId: '1001', priceBasis: 'PROVIDER_ADJUSTED_CLOSE', points: [
  { date: '2025-02-27', close: '100.0000000000', adjustedClose: '99.0000000000', returnValue: 0, state: 'FLAT' },
  { date: '2025-02-28', close: '101.0000000000', adjustedClose: '100.0000000000', returnValue: 0.0101010101010101, state: 'UP' }
] };
export const backtest = { id: '2001', conditionId: '101', datasetId: '501', testStart: '2025-02-20', testEnd: '2025-02-21', horizon: 1,
  eligibleCount: 2, predictedCount: 1, correctCount: 1, skippedCount: 1, coverage: 0.5,
  metrics: { accuracy: 1, precision: [1, null, null], recall: [1, null, null], confusionMatrix: [[1, 0, 0], [0, 0, 0], [0, 0, 0]], brierScore: 0.015,
    logLoss: 0.10536051565782628, majorityAccuracy: 1, persistenceAccuracy: 0, tieCount: 0, skipReasons: { ZERO_ROW_UNESTIMATED: 1 } },
  dataSource, provenance, engineVersion: 'msa-core-v1', createdAt: '2025-03-01T00:00:02Z' };
export const predictions = { page: 0, size: 20, totalElements: 2, items: [
  { originDate: '2025-02-19', targetDate: '2025-02-20', trainStart: '2025-01-06', trainEnd: '2025-02-19', actualState: 'UP', predictedState: 'UP',
    probabilities: [0.9, 0.05, 0.05], majorityState: 'UP', persistenceState: 'FLAT', status: 'SCORED', skipCode: null },
  { originDate: '2025-02-20', targetDate: '2025-02-21', trainStart: '2025-01-06', trainEnd: '2025-02-20', actualState: 'DOWN', predictedState: null,
    probabilities: null, majorityState: null, persistenceState: null, status: 'SKIPPED', skipCode: 'ZERO_ROW_UNESTIMATED' }
] };
export const historyPage = { page: 0, size: 20, totalElements: 2, items: ['ANALYSIS', 'BACKTEST'].map((type, i) => ({ type, id: String(1001 + i * 1000),
  conditionId: '101', stockId: '7203', conditionName: '研究用条件', startDate: '2025-01-06', endDate: '2025-02-28', datasetId: '501',
  predictionStatus: i ? null : 'AVAILABLE', engineVersion: 'msa-core-v1', createdAt: '2025-03-01T00:00:02Z', dataFetchedAt: dataSource.fetchedAt })) };
