package com.sokhasaclinic.clinicbackend.cms.exception;

public class ImageTooLargeException extends CmsException {
    public ImageTooLargeException() {
        super(413, "IMAGE_TOO_LARGE", "Maximum image size is 5 MiB.");
    }

    public ImageTooLargeException(String message) {
        super(413, "IMAGE_TOO_LARGE", message);
    }
}
