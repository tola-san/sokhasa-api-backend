package com.sokhasaclinic.clinicbackend.notification.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TelegramSubscriptionTests {
    private static final Instant CONSENT = Instant.parse("2026-11-20T03:00:00Z");

    @Test
    void consentIsRequiredAndGroupChatsAreRejected() {
        UUID booking = UUID.randomUUID();
        assertThrows(NullPointerException.class, () -> TelegramSubscription.subscribe(null, 123L, CONSENT));
        assertThrows(NullPointerException.class, () -> TelegramSubscription.subscribe(booking, 123L, null));
        assertThrows(IllegalArgumentException.class, () -> TelegramSubscription.subscribe(booking, -100123L, CONSENT));
        assertThrows(IllegalArgumentException.class, () -> TelegramSubscription.subscribe(booking, 0L, CONSENT));
    }

    @Test
    void revocationPreservesConsentAndIsIdempotent() {
        var subscription = TelegramSubscription.subscribe(UUID.randomUUID(), 1234567890123L, CONSENT);
        assertTrue(subscription.isActive());
        assertTrue(subscription.revoke(CONSENT.plusSeconds(60)));
        assertFalse(subscription.isActive());
        assertFalse(subscription.revoke(CONSENT.plusSeconds(120)));
        assertEquals(CONSENT, subscription.getConsentedAt());
        assertEquals(CONSENT.plusSeconds(60), subscription.getRevokedAt());
    }

    @Test
    void invalidRevocationDoesNotChangeSubscription() {
        var subscription = TelegramSubscription.subscribe(UUID.randomUUID(), 123L, CONSENT);
        assertThrows(IllegalArgumentException.class, () -> subscription.revoke(CONSENT.minusSeconds(1)));
        assertTrue(subscription.isActive());
        assertNull(subscription.getRevokedAt());
    }
}
