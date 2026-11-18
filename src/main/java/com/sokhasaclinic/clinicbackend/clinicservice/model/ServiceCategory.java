package com.sokhasaclinic.clinicbackend.clinicservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Group of clinic services, for example {@code diagnostic-imaging}. The name lives in
 * {@link ServiceCategoryTranslation}, one row per language.
 */
@Entity
@Table(name = "service_category", schema = "clinic")
@Getter
public class ServiceCategory {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ServiceCategory() {
    }
}
