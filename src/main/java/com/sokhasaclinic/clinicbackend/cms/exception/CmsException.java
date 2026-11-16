package com.sokhasaclinic.clinicbackend.cms.exception;

/**
 * Base domain exception for CMS operations.
 */
public class CmsException extends RuntimeException {

    private final int status;
    private final String type;

    public CmsException(int status, String type, String message) {
        super(message);
        this.status = status;
        this.type = type;
    }

    public CmsException(int status, String type, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.type = type;
    }

    public int getStatus() {
        return status;
    }

    public String getType() {
        return type;
    }

    public static CmsException invalid(String message) {
        return new CmsException(400, "VALIDATION_ERROR", message);
    }

    public static CmsException missing() {
        return new CmsException(404, "PAGE_NOT_FOUND", "Page translation was not found.");
    }
}
