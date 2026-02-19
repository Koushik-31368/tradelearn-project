package com.tradelearn.server.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Validates that the annotated String field is a supported stock symbol.
 *
 * Usage:
 *   @ValidStockSymbol
 *   private String stockSymbol;
 */
@Documented
@Constraint(validatedBy = StockSymbolValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidStockSymbol {

    String message() default "Invalid or unsupported stock symbol";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
