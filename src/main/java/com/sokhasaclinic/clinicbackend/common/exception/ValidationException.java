package com.sokhasaclinic.clinicbackend.common.exception;

import org.springframework.http.HttpStatus;

public class ValidationException extends ApiException {
    public ValidationException(String message) {
        super(message, "VALIDATION_ERROR", HttpStatus.BAD_REQUEST);
    }

    public ValidationException(String message, String errorType) {
        super(message, errorType, HttpStatus.BAD_REQUEST);
    }
}
