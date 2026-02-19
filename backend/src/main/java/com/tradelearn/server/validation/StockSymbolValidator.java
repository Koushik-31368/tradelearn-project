package com.tradelearn.server.validation;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates that a stock symbol has candle data available.
 *
 * Checks if a file named candles/{SYMBOL}.json exists on the classpath.
 * Falls back to allowing the symbol if sample.json exists (dev mode).
 */
public class StockSymbolValidator implements ConstraintValidator<ValidStockSymbol, String> {

    private static final Logger log = LoggerFactory.getLogger(StockSymbolValidator.class);

    /**
     * Cache of known-valid symbols (populated on first validation).
     * In production, this can be backed by a database query or config file.
     */
    private static final Set<String> KNOWN_SYMBOLS = new HashSet<>();
    private static boolean initialized = false;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return false;
        }

        String symbol = value.toUpperCase().trim();

        // Check if candle data exists for this symbol
        String path = "candles/" + symbol + ".json";
        InputStream is = getClass().getClassLoader().getResourceAsStream(path);
        if (is != null) {
            return true;
        }

        // Fall back to sample.json (development mode)
        InputStream sample = getClass().getClassLoader().getResourceAsStream("candles/sample.json");
        if (sample != null) {
            return true; // In dev mode, accept any symbol
        }

        return false;
    }
}
