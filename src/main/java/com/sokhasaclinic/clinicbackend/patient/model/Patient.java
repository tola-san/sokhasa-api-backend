package com.sokhasaclinic.clinicbackend.patient.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * A person who has requested an appointment, identified by phone number (E.164). Appointments keep
 * their own copy of the submitted name and phone, so this record is never the source of an earlier
 * booking's details.
 */
@Entity
@Table(name = "patient", schema = "clinic")
@Getter
public class Patient {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false, unique = true, length = 16)
    private String phone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Patient() {
    }
}
