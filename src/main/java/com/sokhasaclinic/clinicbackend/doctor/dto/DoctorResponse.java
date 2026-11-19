package com.sokhasaclinic.clinicbackend.doctor.dto;

import com.sokhasaclinic.clinicbackend.doctor.model.DoctorStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DoctorResponse(UUID id, String fullName, DoctorStatus status, int sortOrder,
        long version, OffsetDateTime createdAt, OffsetDateTime updatedAt) { }
