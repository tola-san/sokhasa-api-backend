package com.sokhasaclinic.clinicbackend.cms.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record PublishRequest(
        @NotEmpty(message = "At least one language code must be provided.")
        List<String> languageCodes
) {}
