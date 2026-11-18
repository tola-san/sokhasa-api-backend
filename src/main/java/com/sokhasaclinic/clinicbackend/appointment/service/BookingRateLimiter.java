package com.sokhasaclinic.clinicbackend.appointment.service;

import com.sokhasaclinic.clinicbackend.appointment.exception.AppointmentException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * AP-12: at most {@value #LIMIT} booking requests per client per {@code WINDOW}. Kept in memory, so
 * the limit is per backend instance and resets on restart; enough for a single instance.
 */
@Component
public class BookingRateLimiter {

    static final int LIMIT = 5;
    static final Duration WINDOW = Duration.ofMinutes(10);
    /** Bounds memory if many clients arrive at once: idle clients are dropped first. */
    private static final int MAX_CLIENTS = 10_000;

    private final Map<String, Deque<Instant>> requests = new ConcurrentHashMap<>();
    private final Clock clock;

    public BookingRateLimiter() {
        this(Clock.systemUTC());
    }

    BookingRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Counts one request from {@code client}, or throws {@code 429 RATE_LIMITED} when over the limit. */
    public void acquire(String client) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(WINDOW);
        if (requests.size() > MAX_CLIENTS) {
            requests.entrySet().removeIf(entry -> {
                synchronized (entry.getValue()) {
                    return entry.getValue().isEmpty() || !entry.getValue().peekLast().isAfter(cutoff);
                }
            });
        }
        Deque<Instant> times = requests.computeIfAbsent(client, key -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && !times.peekFirst().isAfter(cutoff)) {
                times.pollFirst();
            }
            if (times.size() >= LIMIT) {
                throw AppointmentException.rateLimited();
            }
            times.addLast(now);
        }
    }
}
