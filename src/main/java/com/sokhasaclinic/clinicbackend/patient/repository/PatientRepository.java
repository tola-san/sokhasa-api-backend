package com.sokhasaclinic.clinicbackend.patient.repository;

import com.sokhasaclinic.clinicbackend.patient.model.Patient;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {
    Optional<Patient> findFirstByPhone(String phone);
}
