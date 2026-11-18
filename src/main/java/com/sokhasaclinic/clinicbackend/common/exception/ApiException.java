package com.sokhasaclinic.clinicbackend.common.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final String errorType;
    private final HttpStatus status;

    public ApiException(String message, String errorType, HttpStatus status) {
        super(message);
        this.errorType = errorType;
        this.status = status;
    }

    public String getErrorType() {
        return errorType;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
