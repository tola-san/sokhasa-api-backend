package com.sokhasaclinic.clinicbackend.doctor.dto;

import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

public record DoctorScheduleRequest(
        @NotNull(message = "Day of week is required.")
        DayOfWeek dayOfWeek,

        @NotNull(message = "Start time is required.")
        LocalTime startTime,

        @NotNull(message = "End time is required.")
        LocalTime endTime,

        @NotNull(message = "Effective from date is required.")
        LocalDate effectiveFrom,

        LocalDate effectiveUntil
) {}
