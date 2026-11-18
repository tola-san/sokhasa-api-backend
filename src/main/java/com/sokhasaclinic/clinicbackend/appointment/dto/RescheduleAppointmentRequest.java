package com.sokhasaclinic.clinicbackend.appointment.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record RescheduleAppointmentRequest(
        UUID doctorId,

        @NotNull(message = "New appointment date is required.")
        @FutureOrPresent(message = "Appointment date must be today or in the future.")
        LocalDate appointmentDate,

        @NotNull(message = "New start time is required.")
        LocalTime startTime,

        String reason
) {}
