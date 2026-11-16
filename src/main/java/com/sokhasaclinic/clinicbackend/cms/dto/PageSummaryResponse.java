package com.sokhasaclinic.clinicbackend.cms.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Summary admin response for CMS page listings.
 */
public record PageSummaryResponse(
        UUID id,
        String slug,
        String status,
        List<PageTitleDto> translations,
        List<String> publishedLanguageCodes,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
