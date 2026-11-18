package com.sokhasaclinic.clinicbackend.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ServiceTranslationDto(
        @NotBlank(message = "Language code is required.")
        @Size(max = 10, message = "Language code must be at most 10 characters.")
        String languageCode,

        @NotBlank(message = "Service name is required.")
        @Size(max = 255, message = "Service name must be at most 255 characters.")
        String name,

        String description
) {}
