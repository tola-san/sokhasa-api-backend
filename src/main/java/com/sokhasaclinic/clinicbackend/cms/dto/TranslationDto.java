package com.sokhasaclinic.clinicbackend.cms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request/response DTO for a CMS page translation.
 */
public record TranslationDto(
        @NotNull
        @Pattern(regexp = "km|en|zh", message = "Language code must be km, en, or zh")
        String languageCode,

        @NotBlank
        @Size(max = 160)
        String title,

        @NotBlank
        @Size(max = 100000)
        String content,

        @Valid
        ImageDto image
) {
}
