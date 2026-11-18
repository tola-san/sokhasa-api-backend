package com.sokhasaclinic.clinicbackend.doctor.dto;

import com.sokhasaclinic.clinicbackend.doctor.model.Doctor;
import java.time.Instant;
import java.util.UUID;

public record DoctorResponse(
        UUID id,
        String name,
        UUID profileImageId,
        String description,
        Doctor.Status status,
        Instant createdAt,
        Instant updatedAt
) {
    public static DoctorResponse fromEntity(Doctor doctor) {
        if (doctor == null) return null;
        return new DoctorResponse(
                doctor.getId(),
                doctor.getName(),
                doctor.getProfileImageId(),
                doctor.getDescription(),
                doctor.getStatus(),
                doctor.getCreatedAt(),
                doctor.getUpdatedAt()
        );
    }
}
