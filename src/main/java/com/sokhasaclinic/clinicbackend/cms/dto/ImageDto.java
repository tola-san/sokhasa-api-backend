package com.sokhasaclinic.clinicbackend.cms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Image reference within a page translation.
 */
public record ImageDto(
        @NotNull
        UUID mediaId,

        @NotBlank
        @Size(max = 200)
        String altText
) {
}
