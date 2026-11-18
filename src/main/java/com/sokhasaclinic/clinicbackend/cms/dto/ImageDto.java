package com.sokhasaclinic.clinicbackend.cms.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ImageDto(
        UUID mediaId,

        @Size(max = 200, message = "Alt text must be at most 200 characters.")
        String altText
) {}
