package com.sokhasaclinic.clinicbackend.doctor.dto;

import com.sokhasaclinic.clinicbackend.doctor.model.Doctor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record DoctorRequest(
        @NotBlank(message = "Doctor name is required.")
        @Size(max = 255, message = "Doctor name must be at most 255 characters.")
        String name,

        UUID profileImageId,

        String description,

        Doctor.Status status
) {}
