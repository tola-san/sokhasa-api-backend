package com.sokhasaclinic.clinicbackend.cms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PageRequest(
        @NotBlank(message = "Slug is required.")
        @Size(min = 1, max = 80, message = "Slug must be between 1 and 80 characters.")
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "Slug must be lowercase ASCII words and digits joined by single hyphens.")
        String slug,

        @NotEmpty(message = "At least one translation is required.")
        List<@Valid TranslationDto> translations
) {}
