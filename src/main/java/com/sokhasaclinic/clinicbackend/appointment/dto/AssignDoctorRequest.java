package com.sokhasaclinic.clinicbackend.appointment.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignDoctorRequest(
        @NotNull(message = "Doctor ID is required.")
        UUID doctorId,

        String reason
) {}
