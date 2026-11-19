package com.sokhasaclinic.clinicbackend.doctor.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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

    @Version
    @Column(nullable = false)
    private long version;

    protected Doctor() {
    }

    public static Doctor create(String name, DoctorStatus status, int sortOrder, Instant now) {
        Doctor doctor = new Doctor();
        doctor.id = UUID.randomUUID();
        doctor.createdAt = now;
        doctor.update(name, status, sortOrder, now);
        return doctor;
    }

    public void update(String name, DoctorStatus status, int sortOrder, Instant now) {
        this.fullName = name;
        this.status = status;
        this.sortOrder = sortOrder;
        this.updatedAt = now;
    }

    public boolean isActive() {
        return status == DoctorStatus.ACTIVE;
    }
}
