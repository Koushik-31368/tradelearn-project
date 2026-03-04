package com.tradelearn.server.validation;

import java.io.InputStream;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates that a stock symbol has candle data available.
 *
 * Checks if a file named candles/{SYMBOL}.json exists on the classpath.
 * Falls back to allowing the symbol if sample.json exists (dev mode).
 */
public class StockSymbolValidator implements ConstraintValidator<ValidStockSymbol, String> {
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
        return sample != null; // In dev mode, accept any symbol
    }
}
