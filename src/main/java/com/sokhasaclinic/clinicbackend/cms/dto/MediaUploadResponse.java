package com.sokhasaclinic.clinicbackend.cms.dto;

import java.util.UUID;

/**
 * Response DTO returned after a successful media upload.
 */
public record MediaUploadResponse(
        UUID id,
        String fileName,
        String mimeType,
        long size
) {
}
