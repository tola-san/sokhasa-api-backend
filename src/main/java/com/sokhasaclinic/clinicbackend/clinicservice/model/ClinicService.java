package com.sokhasaclinic.clinicbackend.clinicservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * A bookable clinic service, such as a colonoscopy or a blood test package. Name, description and
 * image alt text live in {@link ClinicServiceTranslation}, one row per language. Appointments copy
 * {@code durationMinutes} and {@code bufferMinutes} when created (decision BK-07).
 */
@Entity
@Table(name = "service", schema = "clinic")
@Getter
public class ClinicService {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ServiceCategory category;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    /** Turnover time after the appointment. Admin only; never public (decision SV-07). */
    @Column(name = "buffer_minutes", nullable = false)
    private int bufferMinutes;

    @Column(name = "price_usd", precision = 10, scale = 2)
    private BigDecimal priceUsd;

    @Column(name = "original_price_usd", precision = 10, scale = 2)
    private BigDecimal originalPriceUsd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ServiceStatus status;

    @Column(name = "image_media_id")
    private UUID imageMediaId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ClinicService() {
    }

    /**
     * Saves the editable fields (decision SA-02). Callers validate the values first. Always moves
     * {@code updatedAt}, so the row is dirty and {@code version} increases even when only the
     * translations changed.
     */
    public void updateDetails(int newDurationMinutes, int newBufferMinutes, BigDecimal newPriceUsd,
            BigDecimal newOriginalPriceUsd, int newSortOrder, Instant now) {
        durationMinutes = newDurationMinutes;
        bufferMinutes = newBufferMinutes;
        priceUsd = newPriceUsd;
        originalPriceUsd = newOriginalPriceUsd;
        sortOrder = newSortOrder;
        updatedAt = now;
    }

    /** Returns false, and changes nothing, when the service already has {@code newStatus}. */
    public boolean changeStatus(ServiceStatus newStatus, Instant now) {
        if (status == newStatus) {
            return false;
        }
        status = newStatus;
        updatedAt = now;
        return true;
    }
}
