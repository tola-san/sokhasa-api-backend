package com.sokhasaclinic.clinicbackend.cms.dto;

import com.sokhasaclinic.clinicbackend.cms.model.Page;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public record PageSummaryResponse(
        UUID id,
        String slug,
        Page.Status status,
        List<SummaryTranslation> translations,
        List<String> publishedLanguageCodes,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public record SummaryTranslation(String languageCode, String title) {}

    public static PageSummaryResponse fromEntity(Page page) {
        if (page == null) return null;
        List<SummaryTranslation> list = page.getTranslations() != null
                ? page.getTranslations().stream()
                        .map(t -> new SummaryTranslation(t.getLanguageCode(), t.getTitle()))
                        .toList()
                : List.of();

        List<String> publishedLangs = page.getPublishedLanguageCodes() != null
                ? Arrays.asList(page.getPublishedLanguageCodes())
                : List.of();

        return new PageSummaryResponse(
                page.getId(),
                page.getSlug(),
                page.getStatus(),
                list,
                publishedLangs,
                page.getPublishedAt(),
                page.getCreatedAt(),
                page.getUpdatedAt()
        );
    }
}
