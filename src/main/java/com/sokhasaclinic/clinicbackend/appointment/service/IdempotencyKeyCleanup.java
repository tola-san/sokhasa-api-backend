package com.sokhasaclinic.clinicbackend.appointment.service;

import com.sokhasaclinic.clinicbackend.appointment.repository.IdempotencyKeys;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Deletes booking Idempotency-Keys older than 24 hours (AP-03), once an hour. */
@Component
public class IdempotencyKeyCleanup {

    static final Duration KEEP = Duration.ofHours(24);
    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyCleanup.class);

    private final IdempotencyKeys keys;
    private final Clock clock = Clock.systemUTC();

    public IdempotencyKeyCleanup(IdempotencyKeys keys) {
        this.keys = keys;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    public void deleteExpiredKeys() {
        int deleted = keys.deleteCreatedBefore(clock.instant().minus(KEEP));
        if (deleted > 0) {
            log.debug("Deleted {} expired booking idempotency keys", deleted);
        }
    }
}
