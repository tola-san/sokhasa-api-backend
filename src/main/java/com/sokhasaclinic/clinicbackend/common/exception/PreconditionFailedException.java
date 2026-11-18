package com.sokhasaclinic.clinicbackend.common.exception;

import org.springframework.http.HttpStatus;

public class PreconditionFailedException extends ApiException {
    public PreconditionFailedException(String message) {
        super(message, "PAGE_VERSION_MISMATCH", HttpStatus.PRECONDITION_FAILED);
    }

    public PreconditionFailedException(String message, String errorType) {
        super(message, errorType, HttpStatus.PRECONDITION_FAILED);
    }
}
