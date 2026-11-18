package com.sokhasaclinic.clinicbackend.patient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PatientRequest(
        @NotBlank(message = "Patient name is required.")
        @Size(max = 255, message = "Name must be at most 255 characters.")
        String fullName,

        @NotBlank(message = "Phone number is required.")
        @Size(max = 50, message = "Phone must be at most 50 characters.")
        String phone,

        @Size(max = 255, message = "Email must be at most 255 characters.")
        String email
) {}
