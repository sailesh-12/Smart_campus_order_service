package com.smartcampus.common.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Base domain exception carrying an application error code and HTTP status code.
 */
public class ApiException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;
    private final Map<String, String> fieldErrors;
    private final Long retryAfterSeconds;

    public ApiException(String errorCode, HttpStatus status, String message) {
        this(errorCode, status, message, null, null);
    }

    public ApiException(String errorCode, HttpStatus status, String message, Map<String, String> fieldErrors) {
        this(errorCode, status, message, fieldErrors, null);
    }

    public ApiException(String errorCode, HttpStatus status, String message,
                        Map<String, String> fieldErrors, Long retryAfterSeconds) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
        this.fieldErrors = fieldErrors;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * 429 RATE_LIMITED with a Retry-After value (seconds) that GlobalExceptionHandler copies into the header.
     */
    public static ApiException rateLimited(String message, long retryAfterSeconds) {
        return new ApiException("RATE_LIMITED", HttpStatus.TOO_MANY_REQUESTS, message, null, retryAfterSeconds);
    }

    /**
     * 400 with a single field error, used for business validation that Bean Validation cannot express.
     */
    public static ApiException badRequest(String errorCode, String field, String message) {
        return new ApiException(errorCode, HttpStatus.BAD_REQUEST, message,
                field == null ? null : Map.of(field, message));
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
