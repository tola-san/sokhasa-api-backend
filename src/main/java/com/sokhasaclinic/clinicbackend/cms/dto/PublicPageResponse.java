package com.sokhasaclinic.clinicbackend.cms.dto;

/**
 * Public response for a published page translation.
 */
public record PublicPageResponse(
        String slug,
        String status,
        String languageCode,
        String title,
        String content,
        SignedImageDto image
) {
}
