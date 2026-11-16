package com.sokhasaclinic.clinicbackend.cms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request payload for creating or updating a CMS page.
 */
public record SavePageRequest(
        @NotNull
        @Size(max = 80)
        @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*", message = "Slug must contain only lowercase letters, numbers, and single hyphens")
        String slug,

        @NotNull
        @Size(min = 1, max = 3, message = "Translations must contain between 1 and 3 language entries")
        List<@NotNull @Valid TranslationDto> translations
) {
}
