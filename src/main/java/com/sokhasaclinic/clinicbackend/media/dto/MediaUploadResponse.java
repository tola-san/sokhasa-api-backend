package com.sokhasaclinic.clinicbackend.media.dto;

import com.sokhasaclinic.clinicbackend.media.model.Media;
import java.util.UUID;

public record MediaUploadResponse(
        UUID id,
        String fileName,
        String mimeType,
        long size
) {
    public static MediaUploadResponse fromEntity(Media media) {
        if (media == null) return null;
        return new MediaUploadResponse(
                media.getId(),
                media.getFileName(),
                media.getMimeType(),
                media.getFileSize()
        );
    }
}
