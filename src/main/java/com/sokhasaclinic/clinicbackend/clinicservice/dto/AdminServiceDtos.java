package com.sokhasaclinic.clinicbackend.clinicservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Request and response bodies for the admin service routes (approved November 18, 2026,
 * decisions SA-01 to SA-10).
 */
public final class AdminServiceDtos {

    private AdminServiceDtos() {
    }

    /**
     * Service text in one language. In PUT requests, an absent or null {@code imageAltText} keeps
     * the saved one, and a value is only allowed when the service has an image (SI-06). The service
     * layer checks this and the text lengths after trimming.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AdminServiceText(
            String languageCode,
            String name,
            String shortDescription,
            String imageAltText
    ) {
    }

    /** One service with its admin-only fields and every saved language. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AdminService(
            UUID id,
            String slug,
            String categorySlug,
            String categoryName,
            String status,
            int durationMinutes,
            int bufferMinutes,
            BigDecimal priceUsd,
            BigDecimal originalPriceUsd,
            int sortOrder,
            String imageUrl,
            Instant updatedAt,
            long version,
            List<AdminServiceText> translations,
            List<String> missingLanguages
    ) {
    }

    /** {@code PUT /api/v1/admin/services/{id}}. Languages not sent keep their saved text (SA-05). */
    public record AdminServiceUpdate(
            @NotNull @Min(5) @Max(480) Integer durationMinutes,
            @NotNull @Min(0) @Max(60) Integer bufferMinutes,
            @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal priceUsd,
            @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal originalPriceUsd,
            @NotNull @Min(0) @Max(9999) Integer sortOrder,
            @Size(max = 3) List<@Valid @NotNull AdminServiceText> translations
    ) {
    }

    /** One entry of the {@code altTexts} part of {@code PUT …/image} (decision SI-03). */
    public record ServiceImageAltText(String languageCode, String imageAltText) {
    }

    /** A service and its version, sent as the strong ETag {@code "<version>"}. */
    public record VersionedService(AdminService data, long version) {
    }
}
