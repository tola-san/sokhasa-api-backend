package com.sokhasaclinic.clinicbackend.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        Instant timestamp,
        List<FieldErrorItem> errors
) {
    public record FieldErrorItem(String field, String message) {}
}
