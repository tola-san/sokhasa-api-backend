package com.sokhasaclinic.clinicbackend.service.dto;

import com.sokhasaclinic.clinicbackend.service.model.ClinicService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ServiceRequest(
        @NotNull(message = "Duration in minutes is required.")
        @Min(value = 5, message = "Duration must be at least 5 minutes.")
        Integer durationMinutes,

        ClinicService.Status status,

        @NotEmpty(message = "At least one service translation is required.")
        List<@Valid ServiceTranslationDto> translations
) {}
