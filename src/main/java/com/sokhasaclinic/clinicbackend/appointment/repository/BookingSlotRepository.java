package com.sokhasaclinic.clinicbackend.appointment.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Schedule data and overlap reads; mutations run under the date/period capacity lock. */
@Repository
public class BookingSlotRepository {
    private final JdbcTemplate jdbc;

    public BookingSlotRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Window(UUID id, UUID doctorId, LocalTime start, LocalTime end) { }

    public List<Window> windows(UUID serviceId, LocalDate day, boolean lock) {
        List<UUID> lockedDoctors = List.of();
        if (lock) {
            // Management always locks the doctor before its windows. Use the same order here.
            lockedDoctors = jdbc.queryForList("""
                    select d.id from clinic.doctor d where d.status = 'ACTIVE' and d.id in
                        (select w.doctor_id from clinic.doctor_booking_window w where w.service_id = ? and w.day = ?)
                    order by d.id for share
                    """, UUID.class, serviceId, day);
            if (lockedDoctors.isEmpty()) return List.of();
        }
        var parameters = new java.util.HashMap<String, Object>(Map.of("serviceId", serviceId, "day", day));
        parameters.put("doctorIds", lockedDoctors);
        return new NamedParameterJdbcTemplate(jdbc).query("""
                select w.id, w.doctor_id, w.start_time, w.end_time
                from clinic.doctor_booking_window w join clinic.doctor d on d.id = w.doctor_id
                where w.service_id = :serviceId and w.day = :day and d.status = 'ACTIVE'
                """ + (lock ? " and w.doctor_id in (:doctorIds)" : "") +
                " order by w.doctor_id, w.id" + (lock ? " for share of w" : ""), parameters,
                (rs, row) -> new Window(rs.getObject("id", UUID.class), rs.getObject("doctor_id", UUID.class),
                        rs.getObject("start_time", LocalTime.class), rs.getObject("end_time", LocalTime.class)));
    }

    public boolean hasLegacyPending(LocalDate day, String period) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from clinic.appointment
                where status = 'PENDING' and preferred_start_time is null
                  and preferred_date = ? and preferred_period = ?)
                """, Boolean.class, day, period));
    }

    public boolean free(UUID doctorId, Instant start, Instant occupiedEnd, UUID excludedAppointment) {
        return !Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from clinic.appointment a
                where a.status in ('PENDING', 'CONFIRMED', 'ARRIVED', 'IN_PROGRESS', 'COMPLETED')
                  and (cast(? as uuid) is null or a.id <> cast(? as uuid))
                  and (case when a.status = 'PENDING' then a.reserved_doctor_id else a.doctor_id end) = ?
                  and tstzrange(case when a.status = 'PENDING' then a.reserved_start_at else a.start_at end,
                      clinic.plus_minutes(case when a.status = 'PENDING' then a.reserved_end_at else a.end_at end,
                                          a.buffer_minutes))
                      && tstzrange(?, ?))
                """, Boolean.class, excludedAppointment, excludedAppointment, doctorId,
                OffsetDateTime.ofInstant(start, ZoneOffset.UTC), OffsetDateTime.ofInstant(occupiedEnd, ZoneOffset.UTC)));
    }
}
