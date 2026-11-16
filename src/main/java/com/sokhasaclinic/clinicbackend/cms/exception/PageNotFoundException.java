package com.sokhasaclinic.clinicbackend.cms.exception;

public class PageNotFoundException extends CmsException {
    public PageNotFoundException() {
        super(404, "PAGE_NOT_FOUND", "Page translation was not found.");
    }

    public PageNotFoundException(String message) {
        super(404, "PAGE_NOT_FOUND", message);
    }
}
