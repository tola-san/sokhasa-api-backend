package com.sokhasaclinic.clinicbackend.appointment.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sokhasaclinic.clinicbackend.appointment.exception.AppointmentException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class BookingRateLimiterTests {

    private static final class MovableClock extends Clock {
        Instant now = Instant.parse("2026-11-18T03:00:00Z");

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void sixthRequestInTenMinutesIsRateLimited() {
        MovableClock clock = new MovableClock();
        BookingRateLimiter limiter = new BookingRateLimiter(clock);
        for (int i = 0; i < 5; i++) {
            limiter.acquire("203.0.113.7");
            clock.now = clock.now.plusSeconds(30);
        }

        AppointmentException limited = assertThrows(AppointmentException.class, () -> limiter.acquire("203.0.113.7"));

        assertEquals(429, limited.getStatus());
        assertEquals("RATE_LIMITED", limited.getType());
        // Other clients are counted separately.
        assertDoesNotThrow(() -> limiter.acquire("198.51.100.4"));
    }

    @Test
    void oldRequestsStopCountingAfterTheWindow() {
        MovableClock clock = new MovableClock();
        BookingRateLimiter limiter = new BookingRateLimiter(clock);
        for (int i = 0; i < 5; i++) {
            limiter.acquire("203.0.113.7");
        }

        clock.now = clock.now.plus(Duration.ofMinutes(10)).plusSeconds(1);

        assertDoesNotThrow(() -> limiter.acquire("203.0.113.7"));
    }
}
