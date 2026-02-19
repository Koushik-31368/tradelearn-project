package com.tradelearn.server.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Validates that the annotated String field contains a valid trade type.
 * Valid values: BUY, SELL, SHORT, COVER (case-insensitive).
 *
 * Usage:
 *   @ValidTradeType
 *   private String type;
 */
@Documented
@Constraint(validatedBy = TradeTypeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTradeType {

    String message() default "Invalid trade type. Must be BUY, SELL, SHORT, or COVER";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
