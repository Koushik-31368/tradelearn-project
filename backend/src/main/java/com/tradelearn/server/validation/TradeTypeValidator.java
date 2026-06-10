package com.tradelearn.server.validation;

import java.util.List;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates that a trade type is one of the allowed values:
 * BUY, SELL, SHORT, COVER.
 */
public class TradeTypeValidator implements ConstraintValidator<ValidTradeType, String> {

    private static final List<String> VALID_TYPES = List.of("BUY", "SELL", "SHORT", "COVER");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return VALID_TYPES.contains(value.toUpperCase().trim());
    }
}
