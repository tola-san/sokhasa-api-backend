package com.sokhasaclinic.clinicbackend.cms.exception;

public class UnsupportedImageException extends CmsException {
    public UnsupportedImageException() {
        super(415, "UNSUPPORTED_IMAGE", "Use JPEG, PNG or WebP.");
    }

    public UnsupportedImageException(String message) {
        super(415, "UNSUPPORTED_IMAGE", message);
    }
}
