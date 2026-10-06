-- Registration metadata only; no observed market prices are seeded.
INSERT INTO stocks (id, ticker, name, exchange, currency, time_zone, enabled)
VALUES
  (9001, 'TEST', 'Synthetic Test Stock', 'XTKS', 'JPY', 'Asia/Tokyo', TRUE),
  (7203, '7203.T', 'Toyota Motor', 'XTKS', 'JPY', 'Asia/Tokyo', TRUE);
