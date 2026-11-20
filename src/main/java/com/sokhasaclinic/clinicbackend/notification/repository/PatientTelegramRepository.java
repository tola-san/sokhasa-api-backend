package com.sokhasaclinic.clinicbackend.notification.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Read and link data for patient Telegram updates (PT-01 to PT-12). */
@Repository
public class PatientTelegramRepository {

    /** What a patient message shows about one booking, in the booking's language where translated. */
    public record BookingView(UUID id, String reference, String status, String languageCode, String serviceName,
            LocalDate preferredDate, LocalTime preferredStartTime, String preferredPeriod, Instant startAt,
            Instant endAt, String doctorName) { }

    public record Link(UUID appointmentId, Instant expiresAt, Instant usedAt) { }

    public record ReminderTarget(UUID appointmentId, UUID subscriptionId, long chatId) { }

    private final JdbcTemplate jdbc;

    public PatientTelegramRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<BookingView> view(UUID appointmentId) {
        return jdbc.query("""
                SELECT a.id, a.reference, a.status, a.language_code, a.preferred_date, a.preferred_start_time,
                    a.preferred_period, a.start_at, a.end_at, d.full_name,
                    coalesce(
                        (SELECT name FROM clinic.service_translation
                            WHERE service_id = a.service_id AND language_code = a.language_code),
                        (SELECT name FROM clinic.service_translation
                            WHERE service_id = a.service_id AND language_code = 'en'),
                        s.slug) AS service_name
                FROM clinic.appointment a
                JOIN clinic.service s ON s.id = a.service_id
                LEFT JOIN clinic.doctor d ON d.id = a.doctor_id
                WHERE a.id = ?
                """, (rs, row) -> new BookingView(rs.getObject("id", UUID.class), rs.getString("reference"),
                rs.getString("status"), rs.getString("language_code"), rs.getString("service_name"),
                rs.getObject("preferred_date", LocalDate.class), rs.getObject("preferred_start_time", LocalTime.class),
                rs.getString("preferred_period"), instant(rs, "start_at"), instant(rs, "end_at"),
                rs.getString("full_name")), appointmentId).stream().findFirst();
    }

    /** PT-02: stores the hash of a new booking's link code. A replay of the same booking changes nothing. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void insertLink(UUID appointmentId, String codeHash, Instant expiresAt) {
        jdbc.update("""
                INSERT INTO clinic.appointment_telegram_link (appointment_id, code_hash, expires_at)
                VALUES (?, ?, ?) ON CONFLICT (appointment_id) DO NOTHING
                """, appointmentId, codeHash, Timestamp.from(expiresAt));
    }

    /** Locks the link so two START taps with the same code cannot both link it. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Link> lockLink(String codeHash) {
        return jdbc.query("""
                SELECT appointment_id, expires_at, used_at FROM clinic.appointment_telegram_link
                WHERE code_hash = ? FOR UPDATE
                """, (rs, row) -> new Link(rs.getObject(1, UUID.class), instant(rs, "expires_at"),
                instant(rs, "used_at")), codeHash).stream().findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markLinkUsed(UUID appointmentId, Instant now) {
        jdbc.update("UPDATE clinic.appointment_telegram_link SET used_at = ? WHERE appointment_id = ? AND used_at IS NULL",
                Timestamp.from(now), appointmentId);
    }

    /** PT-08: confirmed visits starting in [from, to) whose patient still wants updates. */
    public List<ReminderTarget> reminderTargets(Instant from, Instant to) {
        return jdbc.query("""
                SELECT a.id, t.id, t.chat_id
                FROM clinic.appointment a
                JOIN clinic.telegram_subscription t ON t.appointment_id = a.id AND t.revoked_at IS NULL
                WHERE a.status = 'CONFIRMED' AND a.start_at >= ? AND a.start_at < ?
                ORDER BY a.start_at, a.id
                """, (rs, row) -> new ReminderTarget(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getLong(3)), OffsetDateTime.ofInstant(from, ZoneOffset.UTC), OffsetDateTime.ofInstant(to, ZoneOffset.UTC));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
