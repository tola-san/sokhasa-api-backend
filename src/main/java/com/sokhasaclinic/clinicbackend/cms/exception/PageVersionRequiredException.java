package com.sokhasaclinic.clinicbackend.cms.exception;

public class PageVersionRequiredException extends CmsException {
    public PageVersionRequiredException() {
        super(428, "PAGE_VERSION_REQUIRED", "If-Match is required.");
    }

    public PageVersionRequiredException(String message) {
        super(428, "PAGE_VERSION_REQUIRED", message);
    }
}
