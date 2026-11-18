package com.sokhasaclinic.clinicbackend.clinicservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * A bookable service in one language, as returned by {@code GET /api/v1/public/services}. Every
 * field is always present; optional values are {@code null}, never omitted. Admin-only fields
 * (buffer, status, sort order) are never included (decision SV-07).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PublicServiceResponse(
        UUID id,
        String slug,
        String categorySlug,
        String categoryName,
        String name,
        String shortDescription,
        int durationMinutes,
        BigDecimal priceUsd,
        BigDecimal originalPriceUsd,
        String imageUrl,
        String imageAltText
) {
}
