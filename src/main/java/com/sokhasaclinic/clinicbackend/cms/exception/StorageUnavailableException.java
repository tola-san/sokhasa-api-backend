package com.sokhasaclinic.clinicbackend.cms.exception;

public class StorageUnavailableException extends CmsException {
    public StorageUnavailableException(String message) {
        super(503, "STORAGE_UNAVAILABLE", message);
    }
}
