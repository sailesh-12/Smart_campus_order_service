package com.smartcampus.common.exception;

import com.smartcampus.common.dto.ApiError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler providing a single uniform JSON error shape across the entire API.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex) {
        log.warn("API Exception [{}]: {}", ex.getErrorCode(), ex.getMessage());
        ApiError error = new ApiError(
                Instant.now(),
                ex.getStatus().value(),
                ex.getErrorCode(),
                ex.getMessage(),
                ex.getFieldErrors()
        );
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(ex.getStatus());
        if (ex.getRetryAfterSeconds() != null) {
            builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()));
        }
        return builder.body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return badRequest("Input validation failed", fieldErrors);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException ex) {
        return badRequest("Required header is missing", Map.of(ex.getHeaderName(), "is required"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex) {
        return badRequest("Required parameter is missing", Map.of(ex.getParameterName(), "is required"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return badRequest("Invalid parameter value", Map.of(ex.getName(), "has an invalid value"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return badRequest("Request body is missing or malformed", null);
    }

    /**
     * Rule 5.7: two simultaneous status changes on the same order cannot both win (@Version).
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        ApiError error = new ApiError(Instant.now(), HttpStatus.CONFLICT.value(), "INVALID_TRANSITION",
                "The resource was changed by another request. Reload and try again.", null);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDeniedException(AccessDeniedException ex) {
        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.FORBIDDEN.value(),
                "FORBIDDEN",
                "Access denied",
                null
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex) {
        ApiError error = new ApiError(Instant.now(), HttpStatus.NOT_FOUND.value(), "NOT_FOUND",
                "No endpoint " + ex.getResourcePath(), null);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGenericException(Exception ex) {
        log.error("Unhandled exception: ", ex);
        ApiError error = new ApiError(
                Instant.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_ERROR",
                "An unexpected server error occurred",
                null
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    private ResponseEntity<ApiError> badRequest(String message, Map<String, String> fieldErrors) {
        ApiError error = new ApiError(Instant.now(), HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR", message, fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
}
