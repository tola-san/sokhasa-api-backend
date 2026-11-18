package com.sokhasaclinic.clinicbackend.cms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TranslationDto(
        @NotBlank(message = "Language code is required.")
        @Pattern(regexp = "^(km|en|zh)$", message = "Language code must be km, en, or zh.")
        String languageCode,

        @NotBlank(message = "Title is required.")
        @Size(max = 160, message = "Title must be at most 160 characters.")
        String title,

        @NotBlank(message = "Content is required.")
        @Size(max = 100000, message = "Content cannot exceed 100000 characters.")
        String content,

        @Valid
        ImageDto image
) {}
