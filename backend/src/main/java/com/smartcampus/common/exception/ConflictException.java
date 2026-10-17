package com.smartcampus.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown for state conflicts, stock exhaustion, slot capacity limits, or idempotency reuse (HTTP 409).
 */
public class ConflictException extends ApiException {

    public ConflictException(String errorCode, String message) {
        super(errorCode, HttpStatus.CONFLICT, message);
    }
}
