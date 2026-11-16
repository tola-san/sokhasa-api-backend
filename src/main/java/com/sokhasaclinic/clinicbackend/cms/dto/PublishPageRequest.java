package com.sokhasaclinic.clinicbackend.cms.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request payload for publishing selected page translations.
 */
public record PublishPageRequest(
        @NotNull
        @Size(min = 1, max = 3, message = "Language codes must contain between 1 and 3 language entries")
        List<@NotNull @Pattern(regexp = "km|en|zh", message = "Language code must be km, en, or zh") String> languageCodes
) {
}
