package com.smartcampus.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested resource does not exist (HTTP 404).
 */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super("NOT_FOUND", HttpStatus.NOT_FOUND, message);
    }

    public NotFoundException(String errorCode, String message) {
        super(errorCode, HttpStatus.NOT_FOUND, message);
    }
}
