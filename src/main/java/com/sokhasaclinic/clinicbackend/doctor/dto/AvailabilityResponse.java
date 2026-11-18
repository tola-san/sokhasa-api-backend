package com.sokhasaclinic.clinicbackend.doctor.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record AvailabilityResponse(
        LocalDate date,
        UUID serviceId,
        UUID doctorId,
        List<Slot> slots
) {
    public record Slot(
            LocalTime startTime,
            LocalTime endTime,
            boolean available
    ) {}
}
