package com.sokhasaclinic.clinicbackend.notification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

/**
 * Consent to receive Telegram updates for one booking in a private chat.
 * Internal persistence only: a future approved linking flow must verify booking ownership,
 * a private-chat update and consent before creating this record. It must never accept a
 * patient-supplied chat ID as proof of ownership.
 */
@Entity
@Table(name = "telegram_subscription", schema = "clinic")
@Getter
public class TelegramSubscription {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "appointment_id", nullable = false, updatable = false, unique = true)
    private UUID appointmentId;

    @Column(name = "chat_id", nullable = false, updatable = false)
    private long chatId;

    @Column(name = "consented_at", nullable = false, updatable = false)
    private Instant consentedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected TelegramSubscription() {}

    public static TelegramSubscription subscribe(UUID appointmentId, long privateChatId, Instant consentedAt) {
        Objects.requireNonNull(appointmentId, "Appointment ID is required.");
        Objects.requireNonNull(consentedAt, "Consent time is required.");
        if (privateChatId <= 0) throw new IllegalArgumentException("A positive private chat ID is required.");
        TelegramSubscription subscription = new TelegramSubscription();
        subscription.id = UUID.randomUUID();
        subscription.appointmentId = appointmentId;
        subscription.chatId = privateChatId;
        subscription.consentedAt = consentedAt;
        return subscription;
    }

    public boolean isActive() { return revokedAt == null; }

    /** Revocation is idempotent; preserves the original consent and first revocation time. */
    public boolean revoke(Instant now) {
        Objects.requireNonNull(now, "Revocation time is required.");
        if (!isActive()) return false;
        if (now.isBefore(consentedAt)) throw new IllegalArgumentException("Revocation cannot precede consent.");
        revokedAt = now;
        return true;
    }
}
