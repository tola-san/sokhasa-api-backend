package com.sokhasaclinic.clinicbackend.cms.dto;

import com.sokhasaclinic.clinicbackend.cms.model.Page;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public record PageDetailResponse(
        UUID id,
        String slug,
        Page.Status status,
        List<TranslationDto> translations,
        List<String> publishedLanguageCodes,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static PageDetailResponse fromEntity(Page page) {
        if (page == null) return null;
        List<TranslationDto> list = page.getTranslations() != null
                ? page.getTranslations().stream()
                        .map(t -> new TranslationDto(
                                t.getLanguageCode(),
                                t.getTitle(),
                                t.getContent(),
                                t.getMediaId() != null ? new ImageDto(t.getMediaId(), t.getAltText()) : null
                        ))
                        .toList()
                : List.of();

        List<String> publishedLangs = page.getPublishedLanguageCodes() != null
                ? Arrays.asList(page.getPublishedLanguageCodes())
                : List.of();

        return new PageDetailResponse(
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
