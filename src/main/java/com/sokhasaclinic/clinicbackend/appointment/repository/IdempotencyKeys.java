package com.sokhasaclinic.clinicbackend.appointment.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Stored booking Idempotency-Keys (AP-03). Each key maps to the request's hash and the appointment
 * it created, so a repeated submit returns that appointment instead of booking again.
 */
@Component
public class IdempotencyKeys {

    /** First key of the two-key advisory lock; distinct from the booking period lock. */
    private static final int NAMESPACE = 0x49644b31; // "IdK1"

    public record StoredKey(String requestHash, UUID appointmentId) {
    }

    private final JdbcTemplate jdbc;

    public IdempotencyKeys(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Serialises requests with the same key until this transaction ends. */
    public void lock(UUID key) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("The idempotency key lock needs an active transaction.");
        }
        jdbc.queryForList("select pg_advisory_xact_lock(?, hashtext(?))", NAMESPACE, key.toString());
    }

    public Optional<StoredKey> find(UUID key) {
        return jdbc.query(
                "select request_hash, appointment_id from clinic.booking_idempotency_key where idempotency_key = ?",
                (rs, row) -> new StoredKey(rs.getString(1), rs.getObject(2, UUID.class)), key)
                .stream().findFirst();
    }

    public void save(UUID key, String requestHash, UUID appointmentId, Instant now) {
        jdbc.update("""
                insert into clinic.booking_idempotency_key (idempotency_key, request_hash, appointment_id, created_at)
                values (?, ?, ?, ?)
                """, key, requestHash, appointmentId, Timestamp.from(now));
    }

    /** Deletes keys created before {@code cutoff}; returns how many. */
    public int deleteCreatedBefore(Instant cutoff) {
        return jdbc.update("delete from clinic.booking_idempotency_key where created_at < ?", Timestamp.from(cutoff));
    }
}
