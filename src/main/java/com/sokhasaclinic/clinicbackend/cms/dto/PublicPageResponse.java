package com.sokhasaclinic.clinicbackend.cms.dto;

import com.sokhasaclinic.clinicbackend.cms.model.Page;

public record PublicPageResponse(
        String slug,
        Page.Status status,
        String languageCode,
        String title,
        String content,
        PublicImage image
) {
    public record PublicImage(String url, String altText) {}
}
