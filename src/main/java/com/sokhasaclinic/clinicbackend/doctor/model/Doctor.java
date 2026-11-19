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

    /** Public profile address (PC-05); {@code null} until an admin saves a profile. */
    @Column(length = 80, unique = true)
    private String slug;

    /** DP-03: experience years are derived from this, never stored. */
    @Column(name = "practice_start_year")
    private Integer practiceStartYear;

    @Column(name = "publications_count")
    private Integer publicationsCount;

    /** DP-06: one photo shared by every language. */
    @Column(name = "image_media_id")
    private UUID imageMediaId;

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

    /**
     * Saves the profile fields (PC-05, DP-03, DP-04). Callers validate first. Always moves
     * {@code updatedAt}, so {@code version} increases even when only the translations changed.
     */
    public void updateProfile(String newSlug, Integer newPracticeStartYear, Integer newPublicationsCount, Instant now) {
        slug = newSlug;
        practiceStartYear = newPracticeStartYear;
        publicationsCount = newPublicationsCount;
        updatedAt = now;
    }

    /** Points the doctor at a new photo, or at none with {@code null} (DP-06). */
    public void changeImage(UUID newImageMediaId, Instant now) {
        imageMediaId = newImageMediaId;
        updatedAt = now;
    }

    public boolean isActive() {
        return status == DoctorStatus.ACTIVE;
    }
}
