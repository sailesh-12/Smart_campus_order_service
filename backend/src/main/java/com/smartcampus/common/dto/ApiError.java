package com.smartcampus.common.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Standard API error response shape across the entire application.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        Map<String, String> fieldErrors
) {
    public ApiError(int status, String code, String message) {
        this(Instant.now(), status, code, message, null);
    }

    public ApiError(int status, String code, String message, Map<String, String> fieldErrors) {
        this(Instant.now(), status, code, message, fieldErrors);
    }
}
