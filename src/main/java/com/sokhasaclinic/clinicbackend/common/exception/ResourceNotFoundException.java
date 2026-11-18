package com.sokhasaclinic.clinicbackend.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {
    public ResourceNotFoundException(String message) {
        super(message, "RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND);
    }

    public ResourceNotFoundException(String message, String errorType) {
        super(message, errorType, HttpStatus.NOT_FOUND);
    }
}
