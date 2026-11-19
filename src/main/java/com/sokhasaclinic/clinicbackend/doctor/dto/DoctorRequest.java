package com.sokhasaclinic.clinicbackend.doctor.dto;

import com.sokhasaclinic.clinicbackend.doctor.model.DoctorStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DoctorRequest(@NotBlank @Size(max = 120) String fullName,
        @NotNull DoctorStatus status, @NotNull @Min(0) Integer sortOrder) { }
