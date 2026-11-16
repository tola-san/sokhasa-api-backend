package com.sokhasaclinic.clinicbackend.cms.dto;

/**
 * Public image response with pre-signed access URL and alt text.
 */
public record SignedImageDto(
        String url,
        String altText
) {
}
