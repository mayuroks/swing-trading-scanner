-- Railway migration script
-- Sync schema differences between local and Railway

-- 1. Add missing volume column to stock_history
ALTER TABLE stock_history ADD COLUMN IF NOT EXISTS volume BIGINT DEFAULT 0;

-- 2. Create missing instruments table
CREATE TABLE IF NOT EXISTS instruments (
    instrument_token VARCHAR(20) PRIMARY KEY,
    exchange_token VARCHAR(20),
    tradingsymbol VARCHAR(50),
    name VARCHAR(255),
    last_price NUMERIC(10,2),
    expiry DATE,
    strike NUMERIC(10,2),
    tick_size NUMERIC(10,2),
    lot_size INTEGER,
    instrument_type VARCHAR(20),
    segment VARCHAR(20),
    exchange VARCHAR(20),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Recreate v_stock_analysis view with all metrics
DROP VIEW IF EXISTS v_stock_analysis;

CREATE OR REPLACE VIEW v_stock_analysis AS
SELECT
    instrument_token,
    tradingsymbol,
    name,
    exchange,
    instrument_type,
    trade_date,
    close_price,
    volume,
    avg_volume_30d,
    week52_high,
    week52_low,
    CASE WHEN prev_1d IS NOT NULL AND prev_1d != 0
         THEN ROUND(((close_price::NUMERIC - prev_1d::NUMERIC) / NULLIF(prev_1d::NUMERIC, 0)) * 100, 2) END AS pct_1d,
    CASE WHEN prev_3d IS NOT NULL AND prev_3d != 0
         THEN ROUND(((close_price::NUMERIC - prev_3d::NUMERIC) / NULLIF(prev_3d::NUMERIC, 0)) * 100, 2) END AS pct_3d,
    CASE WHEN prev_7d IS NOT NULL AND prev_7d != 0
         THEN ROUND(((close_price::NUMERIC - prev_7d::NUMERIC) / NULLIF(prev_7d::NUMERIC, 0)) * 100, 2) END AS pct_7d,
    CASE WHEN prev_10d IS NOT NULL AND prev_10d != 0
         THEN ROUND(((close_price::NUMERIC - prev_10d::NUMERIC) / NULLIF(prev_10d::NUMERIC, 0)) * 100, 2) END AS pct_10d,
    CASE WHEN prev_15d IS NOT NULL AND prev_15d != 0
         THEN ROUND(((close_price::NUMERIC - prev_15d::NUMERIC) / NULLIF(prev_15d::NUMERIC, 0)) * 100, 2) END AS pct_15d,
    CASE WHEN prev_1m IS NOT NULL AND prev_1m != 0
         THEN ROUND(((close_price::NUMERIC - prev_1m::NUMERIC) / NULLIF(prev_1m::NUMERIC, 0)) * 100, 2) END AS pct_1m
FROM (
    SELECT
        i.instrument_token,
        i.tradingsymbol,
        i.name,
        i.exchange,
        i.instrument_type,
        sh.trade_date,
        sh.close_price,
        sh.volume,
        AVG(sh.volume) OVER (
            PARTITION BY sh.symbol
            ORDER BY sh.trade_date
            ROWS BETWEEN 29 PRECEDING AND CURRENT ROW
        )::BIGINT AS avg_volume_30d,
        MAX(sh.close_price) OVER (
            PARTITION BY sh.symbol
            ORDER BY sh.trade_date
            ROWS BETWEEN 364 PRECEDING AND CURRENT ROW
        ) AS week52_high,
        MIN(sh.close_price) OVER (
            PARTITION BY sh.symbol
            ORDER BY sh.trade_date
            ROWS BETWEEN 364 PRECEDING AND CURRENT ROW
        ) AS week52_low,
        LAG(sh.close_price, 1) OVER (PARTITION BY sh.symbol ORDER BY sh.trade_date) AS prev_1d,
        LAG(sh.close_price, 3) OVER (PARTITION BY sh.symbol ORDER BY sh.trade_date) AS prev_3d,
        LAG(sh.close_price, 7) OVER (PARTITION BY sh.symbol ORDER BY sh.trade_date) AS prev_7d,
        LAG(sh.close_price, 10) OVER (PARTITION BY sh.symbol ORDER BY sh.trade_date) AS prev_10d,
        LAG(sh.close_price, 15) OVER (PARTITION BY sh.symbol ORDER BY sh.trade_date) AS prev_15d,
        LAG(sh.close_price, 21) OVER (PARTITION BY sh.symbol ORDER BY sh.trade_date) AS prev_1m
    FROM instruments i
    JOIN stock_history sh ON sh.symbol = i.instrument_token
) base;
