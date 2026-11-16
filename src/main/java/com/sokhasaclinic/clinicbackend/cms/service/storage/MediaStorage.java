package com.sokhasaclinic.clinicbackend.cms.service.storage;

/**
 * Storage abstraction for uploading, deleting, and presigning media objects.
 */
public interface MediaStorage {

    /**
     * Uploads media bytes to object storage.
     */
    void upload(String key, String mimeType, byte[] bytes);

    /**
     * Deletes a media object from storage.
     */
    void delete(String key);

    /**
     * Generates a signed URL with temporary access for client downloads.
     */
    String signedUrl(String key);
}
