package com.sokhasaclinic.clinicbackend.appointment.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectAppointmentRequest(
        @NotBlank(message = "Rejection reason is required.")
        String reason
) {}
