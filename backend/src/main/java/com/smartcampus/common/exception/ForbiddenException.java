package com.smartcampus.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an authenticated user lacks permission to access or modify a resource (HTTP 403).
 */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super("FORBIDDEN", HttpStatus.FORBIDDEN, message);
    }

    public ForbiddenException(String errorCode, String message) {
        super(errorCode, HttpStatus.FORBIDDEN, message);
    }
}
