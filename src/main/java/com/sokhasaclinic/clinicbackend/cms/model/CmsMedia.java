package com.sokhasaclinic.clinicbackend.cms.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

/**
 * Entity representing uploaded media metadata stored in R2 / S3.
 */
@Entity
@Table(name = "cms_media", schema = "clinic")
@Getter
public class CmsMedia {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "object_key", nullable = false, unique = true)
    private String objectKey;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(nullable = false)
    private long size;

    protected CmsMedia() {
    }

    public CmsMedia(String objectKey, String fileName, String mimeType, long size) {
        this.objectKey = Objects.requireNonNull(objectKey, "objectKey must not be null");
        this.fileName = Objects.requireNonNull(fileName, "fileName must not be null");
        this.mimeType = Objects.requireNonNull(mimeType, "mimeType must not be null");
        this.size = size;
    }

    public CmsMedia(UUID id, String objectKey, String fileName, String mimeType, long size) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.objectKey = Objects.requireNonNull(objectKey, "objectKey must not be null");
        this.fileName = Objects.requireNonNull(fileName, "fileName must not be null");
        this.mimeType = Objects.requireNonNull(mimeType, "mimeType must not be null");
        this.size = size;
    }
}
