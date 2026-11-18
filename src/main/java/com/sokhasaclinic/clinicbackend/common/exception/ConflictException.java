package com.sokhasaclinic.clinicbackend.common.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {
    public ConflictException(String message) {
        super(message, "CONFLICT", HttpStatus.CONFLICT);
    }

    public ConflictException(String message, String errorType) {
        super(message, errorType, HttpStatus.CONFLICT);
    }
}
