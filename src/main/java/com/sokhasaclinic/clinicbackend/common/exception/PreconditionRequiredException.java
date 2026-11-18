package com.sokhasaclinic.clinicbackend.common.exception;

import org.springframework.http.HttpStatus;

public class PreconditionRequiredException extends ApiException {
    public PreconditionRequiredException(String message) {
        super(message, "PAGE_VERSION_REQUIRED", HttpStatus.PRECONDITION_REQUIRED);
    }

    public PreconditionRequiredException(String message, String errorType) {
        super(message, errorType, HttpStatus.PRECONDITION_REQUIRED);
    }
}
