package com.sokhasaclinic.clinicbackend.patient.service;

import com.sokhasaclinic.clinicbackend.patient.dto.PatientRequest;
import com.sokhasaclinic.clinicbackend.patient.model.Patient;
import com.sokhasaclinic.clinicbackend.patient.repository.PatientRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientService {

    private final PatientRepository patientRepository;

    public PatientService(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    @Transactional
    public Patient findOrCreatePatient(PatientRequest request) {
        Optional<Patient> existing = patientRepository.findFirstByPhone(request.phone().trim());
        if (existing.isPresent()) {
            Patient patient = existing.get();
            patient.setFullName(request.fullName().trim());
            if (request.email() != null) {
                patient.setEmail(request.email().trim());
            }
            patient.setUpdatedAt(Instant.now());
            return patientRepository.save(patient);
        }

        Patient newPatient = Patient.builder()
                .fullName(request.fullName().trim())
                .phone(request.phone().trim())
                .email(request.email() != null ? request.email().trim() : null)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        return patientRepository.save(newPatient);
    }
}
