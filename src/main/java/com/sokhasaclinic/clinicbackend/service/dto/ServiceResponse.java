package com.sokhasaclinic.clinicbackend.service.dto;

import com.sokhasaclinic.clinicbackend.service.model.ClinicService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ServiceResponse(
        UUID id,
        int durationMinutes,
        ClinicService.Status status,
        List<ServiceTranslationDto> translations,
        Instant createdAt,
        Instant updatedAt
) {
    public static ServiceResponse fromEntity(ClinicService service) {
        if (service == null) return null;
        List<ServiceTranslationDto> translationDtos = service.getTranslations() != null
                ? service.getTranslations().stream()
                        .map(t -> new ServiceTranslationDto(t.getLanguageCode(), t.getName(), t.getDescription()))
                        .toList()
                : List.of();

        return new ServiceResponse(
                service.getId(),
                service.getDurationMinutes(),
                service.getStatus(),
                translationDtos,
                service.getCreatedAt(),
                service.getUpdatedAt()
        );
    }
}
