package com.sokhasaclinic.clinicbackend.doctor.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * A doctor as a schedulable clinic resource. Doctors have no login in the MVP. Reception assigns
 * one when confirming an appointment (decision BK-02).
 */
@Entity
@Table(name = "doctor", schema = "clinic")
@Getter
public class Doctor {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DoctorStatus status;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Doctor() {
    }

    public boolean isActive() {
        return status == DoctorStatus.ACTIVE;
    }
}
