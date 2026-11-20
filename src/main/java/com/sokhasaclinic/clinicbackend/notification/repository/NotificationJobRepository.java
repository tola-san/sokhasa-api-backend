package com.sokhasaclinic.clinicbackend.notification.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class NotificationJobRepository {
    /** {@code subscriptionId} is set on patient booking messages, which are sent only while it is active (PT-07). */
    public record Job(UUID id, String chatId, String message, int attempts, UUID leaseToken, UUID subscriptionId) {
        public Job(UUID id, String chatId, String message, int attempts, UUID leaseToken) {
            this(id, chatId, message, attempts, leaseToken, null);
        }
    }
    private final JdbcTemplate jdbc;

    public NotificationJobRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public String serviceName(UUID serviceId) {
        return jdbc.queryForObject("""
                SELECT coalesce((SELECT name FROM clinic.service_translation
                    WHERE service_id = s.id AND language_code = 'en'), s.slug)
                FROM clinic.service s WHERE s.id = ?
                """, String.class, serviceId);
    }

    /** The full name of the patient's preferred doctor (PD-07), or null when there is none. */
    public String doctorName(UUID doctorId) {
        if (doctorId == null) return null;
        return jdbc.query("SELECT full_name FROM clinic.doctor WHERE id = ?",
                rs -> rs.next() ? rs.getString(1) : null, doctorId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(UUID appointmentId, String chatId, String message, Instant createdAt) {
        jdbc.update("""
                INSERT INTO clinic.notification_job
                    (id, appointment_id, event_type, chat_id, message, created_at)
                VALUES (?, ?, 'APPOINTMENT_CREATED', ?, ?, ?)
                ON CONFLICT (appointment_id, event_type, chat_id) DO NOTHING
                """, UUID.randomUUID(), appointmentId, chatId, message, Timestamp.from(createdAt));
    }

    /**
     * Queues a patient message (PT-05, PT-07). Booking messages carry their subscription and are
     * queued at most once per booking, event and chat; a {@code PATIENT_REPLY} has no appointment and
     * may repeat.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueuePatient(UUID appointmentId, String eventType, long chatId, String message,
            UUID subscriptionId, Instant createdAt) {
        jdbc.update("""
                INSERT INTO clinic.notification_job
                    (id, appointment_id, event_type, chat_id, message, recipient, subscription_id, created_at)
                VALUES (?, ?, ?, ?, ?, 'PATIENT', ?, ?)
                ON CONFLICT (appointment_id, event_type, chat_id) DO NOTHING
                """, UUID.randomUUID(), appointmentId, eventType, Long.toString(chatId), message, subscriptionId,
                Timestamp.from(createdAt));
    }

    /** True while the subscription exists and has not been revoked (PT-07, PT-09). */
    public boolean subscriptionActive(UUID subscriptionId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT exists(SELECT 1 FROM clinic.telegram_subscription WHERE id = ? AND revoked_at IS NULL)
                """, Boolean.class, subscriptionId));
    }

    /** The patient stopped updates before this job was sent; it is kept for the record but never sent. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean cancelled(Job job) {
        return jdbc.update("""
                UPDATE clinic.notification_job SET status = 'CANCELLED', lease_token = NULL, lease_until = NULL,
                    last_error_code = 'SUBSCRIPTION_REVOKED'
                WHERE id = ? AND status = 'PROCESSING' AND lease_token = ?
                """, job.id(), job.leaseToken()) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Job> claim(int maxAttempts) {
        // Exhausted leases cannot stay PROCESSING forever after a crash on the last attempt.
        jdbc.update("""
                UPDATE clinic.notification_job SET status = 'FAILED', lease_token = NULL,
                    lease_until = NULL, last_error_code = 'LEASE_EXHAUSTED'
                WHERE attempts >= ? AND (status = 'PENDING' OR
                    (status = 'PROCESSING' AND lease_until <= now()))
                """, maxAttempts);
        return jdbc.query("""
                UPDATE clinic.notification_job SET status = 'PROCESSING', attempts = attempts + 1,
                    lease_token = ?, lease_until = now() + interval '2 minutes'
                WHERE id = (
                    SELECT id FROM clinic.notification_job
                    WHERE attempts < ? AND (
                        (status = 'PENDING' AND next_attempt_at <= now()) OR
                        (status = 'PROCESSING' AND lease_until <= now()))
                    ORDER BY created_at, id FOR UPDATE SKIP LOCKED LIMIT 1
                )
                RETURNING id, chat_id, message, attempts, lease_token, subscription_id
                """, (rs, row) -> new Job(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                rs.getInt(4), rs.getObject(5, UUID.class), rs.getObject(6, UUID.class)),
                UUID.randomUUID(), maxAttempts).stream().findFirst();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean sent(Job job) {
        return jdbc.update("""
                UPDATE clinic.notification_job SET status = 'SENT', sent_at = now(),
                    lease_token = NULL, lease_until = NULL, last_error_code = NULL
                WHERE id = ? AND status = 'PROCESSING' AND lease_token = ?
                """, job.id(), job.leaseToken()) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean failed(Job job, boolean retry, long delaySeconds, String code) {
        return jdbc.update("""
                UPDATE clinic.notification_job SET status = ?, next_attempt_at = now() + (? * interval '1 second'),
                    lease_token = NULL, lease_until = NULL, last_error_code = ?
                WHERE id = ? AND status = 'PROCESSING' AND lease_token = ?
                """, retry ? "PENDING" : "FAILED", delaySeconds, code, job.id(), job.leaseToken()) == 1;
    }
}
