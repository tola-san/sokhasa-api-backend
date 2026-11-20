package com.sokhasaclinic.clinicbackend.dashboard.repository;

import com.sokhasaclinic.clinicbackend.common.time.ClinicTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Grouped counts for the admin dashboard (DS-03 to DS-09). Every query returns totals; none reads
 * patient names or phone numbers. Buckets are clinic-local days, months or years (DS-02).
 */
@Repository
public class DashboardStatsRepository {

    /** A service's requests in a range, with its English name (DS-06). */
    public record ServiceCount(UUID serviceId, String serviceName, long count) {
    }

    /** One booking window of a doctor on the day. */
    public record DoctorWindow(UUID doctorId, LocalTime startTime, LocalTime endTime) {
    }

    /** A doctor's appointments on the day (DS-09). */
    public record DoctorLoad(long patients, boolean inVisit) {
    }

    private final JdbcTemplate jdbc;

    public DashboardStatsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** DS-03: appointments created in {@code [from, to)}, by the first day of their bucket. */
    public Map<LocalDate, Long> requestedByBucket(String unit, Instant from, Instant to) {
        return byBucket("""
                select date_trunc(cast(? as text), a.created_at at time zone cast(? as text))::date, count(*)
                from clinic.appointment a
                where a.created_at >= ? and a.created_at < ?
                group by 1
                """, unit, ClinicTime.ZONE.getId(), utc(from), utc(to));
    }

    /** DS-04: status-history rows changed to {@code toStatus} in {@code [from, to)}, by bucket. */
    public Map<LocalDate, Long> changesByBucket(String toStatus, String unit, Instant from, Instant to) {
        return byBucket("""
                select date_trunc(cast(? as text), h.changed_at at time zone cast(? as text))::date, count(*)
                from clinic.appointment_status_history h
                where h.to_status = ? and h.changed_at >= ? and h.changed_at < ?
                group by 1
                """, unit, ClinicTime.ZONE.getId(), toStatus, utc(from), utc(to));
    }

    /** DS-06: requests in {@code [from, to)} per service, most first, ties by English name. */
    public List<ServiceCount> requestsByService(Instant from, Instant to) {
        return jdbc.query("""
                select a.service_id, coalesce(t.name, '') as name, count(*) as requests
                from clinic.appointment a
                left join clinic.service_translation t on t.service_id = a.service_id and t.language_code = 'en'
                where a.created_at >= ? and a.created_at < ?
                group by a.service_id, t.name
                order by requests desc, name, a.service_id
                """,
                (rs, row) -> new ServiceCount(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3)),
                utc(from), utc(to));
    }

    /** DS-08: every booking window on {@code day}, by doctor and start. */
    public List<DoctorWindow> windowsOn(LocalDate day) {
        return jdbc.query("""
                select w.doctor_id, w.start_time, w.end_time
                from clinic.doctor_booking_window w
                where w.day = ?
                order by w.doctor_id, w.start_time, w.end_time
                """,
                (rs, row) -> new DoctorWindow(rs.getObject(1, UUID.class), rs.getObject(2, LocalTime.class),
                        rs.getObject(3, LocalTime.class)),
                day);
    }

    /** DS-09: confirmed-or-later appointments on {@code day} per doctor, and whether one is in progress. */
    public Map<UUID, DoctorLoad> loadOn(LocalDate day) {
        Map<UUID, DoctorLoad> loads = new HashMap<>();
        jdbc.query("""
                select a.doctor_id, count(*), bool_or(a.status = 'IN_PROGRESS')
                from clinic.appointment a
                where a.confirmed_date = ? and a.doctor_id is not null
                  and a.status in ('CONFIRMED', 'ARRIVED', 'IN_PROGRESS', 'COMPLETED')
                group by a.doctor_id
                """,
                rs -> {
                    loads.put(rs.getObject(1, UUID.class), new DoctorLoad(rs.getLong(2), rs.getBoolean(3)));
                },
                day);
        return loads;
    }

    private Map<LocalDate, Long> byBucket(String sql, Object... args) {
        Map<LocalDate, Long> counts = new HashMap<>();
        jdbc.query(sql, rs -> {
            counts.put(rs.getObject(1, LocalDate.class), rs.getLong(2));
        }, args);
        return counts;
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
