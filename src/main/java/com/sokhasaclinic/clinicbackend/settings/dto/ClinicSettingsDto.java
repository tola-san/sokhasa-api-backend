package com.sokhasaclinic.clinicbackend.settings.dto;

import com.sokhasaclinic.clinicbackend.settings.model.ClinicSettings;
import jakarta.validation.constraints.NotBlank;
import java.util.Arrays;
import java.util.List;

public record ClinicSettingsDto(
        @NotBlank(message = "Clinic name is required.")
        String name,

        String phone,
        String email,
        String address,
        String timezone,
        List<String> supportedLanguages
) {
    public static ClinicSettingsDto fromEntity(ClinicSettings settings) {
        if (settings == null) return null;
        List<String> langs = settings.getSupportedLanguages() != null
                ? Arrays.asList(settings.getSupportedLanguages())
                : List.of("km", "en", "zh");

        return new ClinicSettingsDto(
                settings.getName(),
                settings.getPhone(),
                settings.getEmail(),
                settings.getAddress(),
                settings.getTimezone(),
                langs
        );
    }
}
