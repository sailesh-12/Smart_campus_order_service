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

    public ApiException(String errorCode, HttpStatus status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
        this.fieldErrors = null;
    }

    public ApiException(String errorCode, HttpStatus status, String message, Map<String, String> fieldErrors) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
        this.fieldErrors = fieldErrors;
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
}
