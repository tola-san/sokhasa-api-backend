package com.sokhasaclinic.clinicbackend.doctor.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.UUID;

public final class BookingWindowDtos {
    private BookingWindowDtos() { }

    public record Request(@NotNull UUID serviceId, @NotNull LocalDate date,
            @NotBlank @Pattern(regexp = "(?:[01][0-9]|2[0-3]):[0-5][0-9]") String startTime,
            @NotBlank @Pattern(regexp = "(?:[01][0-9]|2[0-3]):[0-5][0-9]") String endTime) { }

    public record Response(UUID id, UUID doctorId, UUID serviceId, LocalDate date,
            String startTime, String endTime, long version) { }
}
