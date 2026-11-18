package com.sokhasaclinic.clinicbackend.appointment.dto;

import com.sokhasaclinic.clinicbackend.patient.dto.PatientRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CreateAppointmentRequest(
        @NotNull(message = "Patient information is required.")
        @Valid
        PatientRequest patient,

        @NotNull(message = "Service ID is required.")
        UUID serviceId,

        UUID preferredDoctorId,

        @NotNull(message = "Appointment date is required.")
        @FutureOrPresent(message = "Appointment date must be today or in the future.")
        LocalDate appointmentDate,

        @NotNull(message = "Start time is required.")
        LocalTime startTime,

        String notes
) {}
