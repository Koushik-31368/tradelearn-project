#!/usr/bin/env python3
"""
TradeLearn — NSE Historical Market Data Ingestion Script
=========================================================

Downloads daily OHLCV candle data for a fixed watchlist of NSE stocks
using yfinance and upserts the results into the Neon PostgreSQL database.

Usage
-----
    pip install -r requirements.txt
    python ingest_market_data.py [--symbols SYM1 SYM2 ...] [--days 365]

Environment variables (set in .env or system environment):
    NEON_DATABASE_URL   — full DSN, e.g. postgresql://user:pass@host/db?sslmode=require
                          OR provide individual parts below:
    DB_HOST             — Postgres host
    DB_PORT             — Postgres port (default: 5432)
    DB_NAME             — Database name
    DB_USER             — Database user
    DB_PASS             — Database password
    DB_SSL_MODE         — sslmode parameter (default: require for Neon)

Design
------
- Idempotent: runs daily via cron without duplication. Uses
  ON CONFLICT (ticker, trade_date) DO UPDATE to refresh any rows
  that may have been adjusted (splits, dividends).
- Each symbol is downloaded independently; a failure on one symbol
  is logged and skipped without aborting the rest.
- Progress is printed to stdout so Render cron logs are readable.
- After successful ingestion, stock_symbols.last_ingested_at is updated.
"""

import os
import sys
import argparse
import logging
from datetime import date, timedelta, datetime

import yfinance as yf
import psycopg2
import psycopg2.extras
from dotenv import load_dotenv

# ── Logging ────────────────────────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
log = logging.getLogger(__name__)

# ── Watchlist ───────────────────────────────────────────────────────────────
# Maps yfinance ticker → bare ticker (as stored in games.stock_symbol).
# Only REPLAY mode symbols are ingested here; LIVE_US symbols are fed
# by the Finnhub WebSocket at runtime.
NSE_WATCHLIST: dict[str, str] = {
    "RELIANCE.NS":   "RELIANCE",
    "TCS.NS":        "TCS",
    "INFY.NS":       "INFY",
    "HDFCBANK.NS":   "HDFCBANK",
    "ICICIBANK.NS":  "ICICIBANK",
    "WIPRO.NS":      "WIPRO",
    "SBIN.NS":       "SBIN",
    "ADANIENT.NS":   "ADANIENT",
    "BAJFINANCE.NS": "BAJFINANCE",
    "HINDUNILVR.NS": "HINDUNILVR",
}

# ── DB connection ───────────────────────────────────────────────────────────

def build_dsn() -> str:
    """Build a PostgreSQL DSN from env vars, preferring NEON_DATABASE_URL."""
    load_dotenv()  # loads .env if present

    dsn = os.getenv("NEON_DATABASE_URL")
    if dsn:
        return dsn

    host = os.getenv("DB_HOST", "localhost")
    port = os.getenv("DB_PORT", "5432")
    name = os.getenv("DB_NAME", "tradelearn")
    user = os.getenv("DB_USER", "postgres")
    password = os.getenv("DB_PASS", "")
    sslmode = os.getenv("DB_SSL_MODE", "require")

    return f"postgresql://{user}:{password}@{host}:{port}/{name}?sslmode={sslmode}"


def get_connection(dsn: str):
    """Return a psycopg2 connection. Raises on failure."""
    try:
        conn = psycopg2.connect(dsn)
        conn.autocommit = False
        return conn
    except psycopg2.OperationalError as e:
        log.error("Failed to connect to database: %s", e)
        raise

# ── Ingestion ───────────────────────────────────────────────────────────────

UPSERT_CANDLE_SQL = """
    INSERT INTO stock_candles_daily
        (ticker, trade_date, open_price, high_price, low_price, close_price, volume)
    VALUES %s
    ON CONFLICT (ticker, trade_date)
    DO UPDATE SET
        open_price  = EXCLUDED.open_price,
        high_price  = EXCLUDED.high_price,
        low_price   = EXCLUDED.low_price,
        close_price = EXCLUDED.close_price,
        volume      = EXCLUDED.volume;
"""

UPDATE_INGESTED_AT_SQL = """
    UPDATE stock_symbols
    SET last_ingested_at = %s
    WHERE ticker = %s;
"""


def ingest_symbol(conn, ticker: str, start: date, end: date) -> int:
    """
    Download daily candles for one ticker from yfinance and upsert into DB.
    Returns the number of rows upserted. Rolls back on any error.
    """
    log.info("[%s] Downloading %s → %s ...", ticker, start, end)

    try:
        df = yf.download(
            tickers=ticker,
            start=start.isoformat(),
            end=end.isoformat(),
            interval="1d",
            auto_adjust=True,   # adjusts for splits/dividends
            progress=False,
        )
    except Exception as e:
        log.error("[%s] yfinance download failed: %s", ticker, e)
        return 0

    if df is None or df.empty:
        log.warning("[%s] No data returned by yfinance", ticker)
        return 0

    # yfinance returns a multi-level column index when downloading one ticker
    # with auto_adjust=True. Flatten if needed.
    if hasattr(df.columns, "levels"):
        df.columns = df.columns.get_level_values(0)

    required = {"Open", "High", "Low", "Close", "Volume"}
    missing = required - set(df.columns)
    if missing:
        log.error("[%s] Missing columns: %s", ticker, missing)
        return 0

    rows = []
    for trade_date, row in df.iterrows():
        trade_date_val = trade_date.date() if hasattr(trade_date, "date") else trade_date
        rows.append((
            ticker,
            trade_date_val,
            float(row["Open"]),
            float(row["High"]),
            float(row["Low"]),
            float(row["Close"]),
            int(row["Volume"]) if not __import__("math").isnan(row["Volume"]) else 0,
        ))

    if not rows:
        log.warning("[%s] DataFrame had no parseable rows", ticker)
        return 0

    try:
        with conn.cursor() as cur:
            psycopg2.extras.execute_values(cur, UPSERT_CANDLE_SQL, rows, page_size=500)
            now = datetime.utcnow()
            cur.execute(UPDATE_INGESTED_AT_SQL, (now, ticker))
        conn.commit()
        log.info("[%s] ✓ Upserted %d rows", ticker, len(rows))
        return len(rows)
    except Exception as e:
        conn.rollback()
        log.error("[%s] DB upsert failed: %s", ticker, e)
        return 0


def ensure_symbols_exist(conn, watchlist: dict[str, str]) -> None:
    """
    Ensure all watchlist symbols are present in stock_symbols.
    The V7 migration seeds them, but this guards against fresh deploys
    running the script before the seed runs.
    """
    display_map = {
        "RELIANCE.NS":   "Reliance Industries",
        "TCS.NS":        "Tata Consultancy Services",
        "INFY.NS":       "Infosys",
        "HDFCBANK.NS":   "HDFC Bank",
        "ICICIBANK.NS":  "ICICI Bank",
        "WIPRO.NS":      "Wipro",
        "SBIN.NS":       "SBIN",
        "ADANIENT.NS":   "Adani Enterprises",
        "BAJFINANCE.NS": "Bajaj Finance",
        "HINDUNILVR.NS": "Hindustan Unilever",
    }
    rows = [
        (ticker, bare, display_map.get(ticker, bare), "NSE", "REPLAY")
        for ticker, bare in watchlist.items()
    ]
    sql = """
        INSERT INTO stock_symbols (ticker, bare_ticker, display_name, exchange, data_mode)
        VALUES %s
        ON CONFLICT (ticker) DO NOTHING;
    """
    try:
        with conn.cursor() as cur:
            psycopg2.extras.execute_values(cur, sql, rows)
        conn.commit()
    except Exception as e:
        conn.rollback()
        log.error("Failed to ensure symbols: %s", e)
        raise


# ── Entrypoint ──────────────────────────────────────────────────────────────

def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Ingest NSE historical candle data into TradeLearn PostgreSQL"
    )
    parser.add_argument(
        "--symbols",
        nargs="+",
        metavar="TICKER",
        help="yfinance tickers to ingest (default: full NSE watchlist)",
    )
    parser.add_argument(
        "--days",
        type=int,
        default=730,
        help="Number of calendar days of history to download (default: 730 = ~2 years)",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Download data but do NOT write to DB (useful for testing)",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    # Resolve ticker list
    if args.symbols:
        # Allow bare tickers (e.g. RELIANCE) or full yfinance form (RELIANCE.NS)
        reverse_map = {v: k for k, v in NSE_WATCHLIST.items()}
        selected: dict[str, str] = {}
        for sym in args.symbols:
            if sym in NSE_WATCHLIST:
                selected[sym] = NSE_WATCHLIST[sym]
            elif sym in reverse_map:
                full = reverse_map[sym]
                selected[full] = sym
            else:
                log.warning("Unknown symbol '%s' — skipping", sym)
        if not selected:
            log.error("No valid symbols to ingest")
            return 1
    else:
        selected = NSE_WATCHLIST

    end_date = date.today()
    start_date = end_date - timedelta(days=args.days)

    log.info("=" * 60)
    log.info("TradeLearn Market Data Ingestion")
    log.info("Symbols : %d (%s)", len(selected), ", ".join(selected.keys()))
    log.info("Window  : %s → %s (%d days)", start_date, end_date, args.days)
    log.info("Dry run : %s", args.dry_run)
    log.info("=" * 60)

    if args.dry_run:
        for ticker in selected:
            log.info("[%s] Downloading (dry run) ...", ticker)
            df = yf.download(ticker, start=start_date.isoformat(),
                             end=end_date.isoformat(), interval="1d",
                             auto_adjust=True, progress=False)
            log.info("[%s] Would upsert %d rows", ticker, len(df))
        return 0

    dsn = build_dsn()
    conn = get_connection(dsn)

    try:
        ensure_symbols_exist(conn, selected)

        total_rows = 0
        failed = []

        for ticker in selected:
            rows_written = ingest_symbol(conn, ticker, start_date, end_date)
            if rows_written == 0:
                failed.append(ticker)
            else:
                total_rows += rows_written

        log.info("=" * 60)
        log.info("Ingestion complete. Total rows: %d", total_rows)
        if failed:
            log.warning("Failed symbols (%d): %s", len(failed), ", ".join(failed))
            return 1
        log.info("All symbols ingested successfully.")
        return 0

    finally:
        conn.close()


if __name__ == "__main__":
    sys.exit(main())
