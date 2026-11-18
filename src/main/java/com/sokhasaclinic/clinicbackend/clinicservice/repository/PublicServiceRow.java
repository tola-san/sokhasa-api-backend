package com.sokhasaclinic.clinicbackend.clinicservice.repository;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One active service with its category, in one language, as read for the public list. The image
 * is still a media id here; the service layer turns it into a signed URL.
 */
public record PublicServiceRow(
        UUID id,
        String slug,
        String categorySlug,
        String categoryName,
        String name,
        String shortDescription,
        int durationMinutes,
        BigDecimal priceUsd,
        BigDecimal originalPriceUsd,
        UUID imageMediaId,
        String imageAltText
) {
}
