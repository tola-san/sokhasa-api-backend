package com.sokhasaclinic.clinicbackend.cms.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Detailed admin response for a CMS page.
 */
public record PageDetailResponse(
        UUID id,
        String slug,
        String status,
        List<TranslationDto> translations,
        List<String> publishedLanguageCodes,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
