package com.sokhasaclinic.clinicbackend.patient.dto;

import com.sokhasaclinic.clinicbackend.patient.model.Patient;
import java.util.UUID;

public record PatientResponse(
        UUID id,
        String fullName,
        String phone,
        String email
) {
    public static PatientResponse fromEntity(Patient patient) {
        if (patient == null) return null;
        return new PatientResponse(
                patient.getId(),
                patient.getFullName(),
                patient.getPhone(),
                patient.getEmail()
        );
    }
}
