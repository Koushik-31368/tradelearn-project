package com.tradelearn.server.exception;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import com.tradelearn.server.util.GameLogger;

/**
 * Global exception handler for REST errors.
 *
 * Provides:
 *   - Structured JSON error responses with timestamps
 *   - Comprehensive logging with context
 *   - Consistent error format across the API
 *   - Validation error field-level reporting
 *   - Protection against information leakage in production
 *
 * All custom game exceptions map to appropriate HTTP status codes.
 * Unknown exceptions return 500 with a generic message (no stack leak).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Standard error response format returned by all exception handlers.
     */
    public static class ErrorResponse {
        public String timestamp;
        public int status;
        public String error;
        public String message;
        public String path;
        public Map<String, Object> details = new HashMap<>();

        public ErrorResponse(int status, String error, String message, String path) {
            this.timestamp = Instant.now().toString();
            this.status = status;
            this.error = error;
            this.message = message;
            this.path = path;
        }

        public ErrorResponse addDetail(String key, Object value) {
            details.put(key, value);
            return this;
        }
    }

    // ==================== GAME-SPECIFIC EXCEPTIONS ====================

    @ExceptionHandler(GameNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleGameNotFound(GameNotFoundException ex, WebRequest request) {
        GameLogger.logError(log, "Game not found", ex, Map.of(
                "gameId", ex.getGameId(),
                "path", request.getDescription(false)
        ));

        ErrorResponse response = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "Game Not Found",
                ex.getMessage(),
                request.getDescription(false)
        ).addDetail("gameId", ex.getGameId());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(RoomFullException.class)
    public ResponseEntity<ErrorResponse> handleRoomFull(RoomFullException ex, WebRequest request) {
        GameLogger.logError(log, "Room full", ex, Map.of(
                "gameId", ex.getGameId(),
                "currentPlayers", ex.getCurrentPlayers(),
                "maxPlayers", ex.getMaxPlayers()
        ));

        ErrorResponse response = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                "Room Full",
                ex.getMessage(),
                request.getDescription(false)
        ).addDetail("gameId", ex.getGameId())
         .addDetail("currentPlayers", ex.getCurrentPlayers())
         .addDetail("maxPlayers", ex.getMaxPlayers());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(InvalidGameStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidGameState(InvalidGameStateException ex, WebRequest request) {
        GameLogger.logError(log, "Invalid game state", ex, Map.of(
                "gameId", ex.getGameId(),
                "currentState", ex.getCurrentState(),
                "expectedState", ex.getExpectedState()
        ));

        ErrorResponse response = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Invalid Game State",
                ex.getMessage(),
                request.getDescription(false)
        ).addDetail("gameId", ex.getGameId())
         .addDetail("currentState", ex.getCurrentState())
         .addDetail("expectedState", ex.getExpectedState());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(TradeValidationException.class)
    public ResponseEntity<ErrorResponse> handleTradeValidation(TradeValidationException ex, WebRequest request) {
        GameLogger.logTradeRejected(log, ex.getGameId(), ex.getUserId(),
                ex.getTradeType(), ex.getQuantity(), ex.getMessage());

        ErrorResponse response = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Trade Validation Failed",
                ex.getMessage(),
                request.getDescription(false)
        ).addDetail("gameId", ex.getGameId())
         .addDetail("userId", ex.getUserId())
         .addDetail("tradeType", ex.getTradeType())
         .addDetail("quantity", ex.getQuantity());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // ==================== VALIDATION EXCEPTIONS ====================

    /**
     * Handles @Valid / @Validated constraint violations.
     * Returns field-level error messages for the frontend.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex, WebRequest request) {

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );

        log.warn("[Validation] {} field errors on {}: {}",
                fieldErrors.size(), request.getDescription(false), fieldErrors);

        ErrorResponse response = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Validation Failed",
                "Request validation failed. Check 'details.fieldErrors' for specifics.",
                request.getDescription(false)
        ).addDetail("fieldErrors", fieldErrors)
         .addDetail("totalErrors", fieldErrors.size());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // ==================== STANDARD EXCEPTIONS ====================

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, WebRequest request) {
        GameLogger.logError(log, "Illegal argument", ex, Map.of(
                "path", request.getDescription(false)
        ));

        ErrorResponse response = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                ex.getMessage(),
                request.getDescription(false)
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex, WebRequest request) {
        GameLogger.logError(log, "Illegal state", ex, Map.of(
                "path", request.getDescription(false)
        ));

        ErrorResponse response = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                "Conflict",
                ex.getMessage(),
                request.getDescription(false)
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    // ==================== CATCH-ALL ====================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, WebRequest request) {
        GameLogger.logError(log, "Unexpected error", ex, Map.of(
                "path", request.getDescription(false),
                "exceptionType", ex.getClass().getName()
        ));

        // Never leak internal details in production
        String message = "An internal error occurred. Please try again or contact support.";

        ErrorResponse response = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                message,
                request.getDescription(false)
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
