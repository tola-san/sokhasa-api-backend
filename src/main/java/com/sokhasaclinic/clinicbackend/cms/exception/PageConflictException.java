package com.sokhasaclinic.clinicbackend.cms.exception;

public class PageConflictException extends CmsException {
    public PageConflictException(String type, String message) {
        super(409, type, message);
    }

    public PageConflictException(String message) {
        super(409, "PAGE_CONFLICT", message);
    }
}
