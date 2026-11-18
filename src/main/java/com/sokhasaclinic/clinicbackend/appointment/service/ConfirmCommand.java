package com.sokhasaclinic.clinicbackend.appointment.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Reception's confirm decision (confirm rules, November 17, 2026). {@code date} and
 * {@code startTime} are local clinic time. {@code durationMinutes} is optional; {@code null} keeps
 * the duration copied from the service.
 */
public record ConfirmCommand(
        UUID doctorId,
        LocalDate date,
        LocalTime startTime,
        Integer durationMinutes) {
}
