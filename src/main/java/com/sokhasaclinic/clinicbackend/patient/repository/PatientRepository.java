package com.sokhasaclinic.clinicbackend.patient.repository;

import com.sokhasaclinic.clinicbackend.patient.model.Patient;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Patient lookups for the booking workflow. */
public interface PatientRepository extends JpaRepository<Patient, UUID> {

    Optional<Patient> findByPhone(String phone);

    /**
     * Creates the patient unless one with the phone already exists. {@code ON CONFLICT DO NOTHING}
     * means two first bookings from the same phone at the same moment cannot fail on the unique
     * constraint. The existing patient's name is never changed.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into clinic.patient (id, full_name, phone, created_at, updated_at)
            values (:id, :fullName, :phone, :now, :now)
            on conflict (phone) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("fullName") String fullName,
            @Param("phone") String phone,
            @Param("now") Instant now);
}
