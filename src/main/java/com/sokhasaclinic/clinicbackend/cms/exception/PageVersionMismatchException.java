package com.sokhasaclinic.clinicbackend.cms.exception;

public class PageVersionMismatchException extends CmsException {
    public PageVersionMismatchException() {
        super(412, "PAGE_VERSION_MISMATCH", "Reload the page before editing.");
    }
    public PageVersionMismatchException(String message) {
        super(412, "PAGE_VERSION_MISMATCH", message);
    }
}
