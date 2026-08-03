-- =========================================================
-- V7: Market Data Layer
-- Adds NSE stock symbol registry, daily OHLCV candle store,
-- and per-game replay session mapping.
--
-- Design notes:
--   - stock_candles_daily uses a UNIQUE (ticker, trade_date)
--     constraint so the Python ingestion script can safely
--     ON CONFLICT DO UPDATE (idempotent upserts).
--   - game_replay_sessions decouples "which market window does
--     this game replay" from the games table, keeping Game.java
--     and its optimistic-lock version column untouched.
--   - 5-minute candles are NOT included in this migration;
--     they will be added in a future V8 when needed.
-- =========================================================

-- ---------------------------------------------------------
-- 1. Symbol registry
-- ---------------------------------------------------------
CREATE TABLE stock_symbols (
    id               BIGSERIAL    PRIMARY KEY,
    ticker           VARCHAR(20)  NOT NULL UNIQUE,  -- yfinance form, e.g. "RELIANCE.NS"
    bare_ticker      VARCHAR(20)  NOT NULL UNIQUE,  -- game form, e.g. "RELIANCE"
    display_name     VARCHAR(100) NOT NULL,
    exchange         VARCHAR(10)  NOT NULL DEFAULT 'NSE',
    data_mode        VARCHAR(10)  NOT NULL DEFAULT 'REPLAY', -- REPLAY | LIVE_US
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    last_ingested_at TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------------------
-- 2. Daily OHLCV candles
-- ---------------------------------------------------------
CREATE TABLE stock_candles_daily (
    id         BIGSERIAL    PRIMARY KEY,
    ticker     VARCHAR(20)  NOT NULL REFERENCES stock_symbols(ticker) ON DELETE CASCADE,
    trade_date DATE         NOT NULL,
    open_price NUMERIC(14,4) NOT NULL,
    high_price NUMERIC(14,4) NOT NULL,
    low_price  NUMERIC(14,4) NOT NULL,
    close_price NUMERIC(14,4) NOT NULL,
    volume     BIGINT        NOT NULL,
    UNIQUE (ticker, trade_date)
);

-- Covering index: enables the "SELECT ... WHERE ticker = ? AND trade_date BETWEEN ? AND ?"
-- slice query to run as an index-only scan.
CREATE INDEX idx_scd_ticker_date ON stock_candles_daily (ticker, trade_date);

-- ---------------------------------------------------------
-- 3. Per-game replay session (links a game to a candle window)
-- ---------------------------------------------------------
CREATE TABLE game_replay_sessions (
    game_id        BIGINT       PRIMARY KEY REFERENCES games(id) ON DELETE CASCADE,
    ticker         VARCHAR(20)  NOT NULL REFERENCES stock_symbols(ticker),
    resolution     VARCHAR(10)  NOT NULL DEFAULT 'daily',
    window_start   DATE         NOT NULL,
    window_end     DATE         NOT NULL,
    candle_count   INT          NOT NULL DEFAULT 0,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------------------
-- 4. Seed: initial NSE watchlist
-- Bare tickers match games.stock_symbol (stored without .NS suffix).
-- ---------------------------------------------------------
INSERT INTO stock_symbols (ticker, bare_ticker, display_name, exchange, data_mode) VALUES
    ('RELIANCE.NS',    'RELIANCE',    'Reliance Industries',     'NSE', 'REPLAY'),
    ('TCS.NS',         'TCS',         'Tata Consultancy Services','NSE', 'REPLAY'),
    ('INFY.NS',        'INFY',        'Infosys',                 'NSE', 'REPLAY'),
    ('HDFCBANK.NS',    'HDFCBANK',    'HDFC Bank',               'NSE', 'REPLAY'),
    ('ICICIBANK.NS',   'ICICIBANK',   'ICICI Bank',              'NSE', 'REPLAY'),
    ('WIPRO.NS',       'WIPRO',       'Wipro',                   'NSE', 'REPLAY'),
    ('SBIN.NS',        'SBIN',        'State Bank of India',     'NSE', 'REPLAY'),
    ('ADANIENT.NS',    'ADANIENT',    'Adani Enterprises',       'NSE', 'REPLAY'),
    ('BAJFINANCE.NS',  'BAJFINANCE',  'Bajaj Finance',           'NSE', 'REPLAY'),
    ('HINDUNILVR.NS',  'HINDUNILVR',  'Hindustan Unilever',      'NSE', 'REPLAY'),
    -- US live symbols (Finnhub mode — candle data not required, ticker is bare)
    ('AAPL',           'AAPL',        'Apple Inc.',              'NASDAQ', 'LIVE_US'),
    ('TSLA',           'TSLA',        'Tesla Inc.',              'NASDAQ', 'LIVE_US'),
    ('MSFT',           'MSFT',        'Microsoft Corp.',         'NASDAQ', 'LIVE_US'),
    ('GOOGL',          'GOOGL',       'Alphabet Inc.',           'NASDAQ', 'LIVE_US'),
    ('AMZN',           'AMZN',        'Amazon.com Inc.',         'NASDAQ', 'LIVE_US')
ON CONFLICT (ticker) DO NOTHING;
