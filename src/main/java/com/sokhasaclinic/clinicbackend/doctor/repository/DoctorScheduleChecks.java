package com.sokhasaclinic.clinicbackend.doctor.repository;

import com.sokhasaclinic.clinicbackend.common.time.ClinicTime;
import com.sokhasaclinic.clinicbackend.doctor.model.DoctorBookingWindow;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Cross-module scheduling checks, run while holding the doctor's exclusive row lock. */
@Repository
public class DoctorScheduleChecks {
    private final JdbcTemplate jdbc;

    public DoctorScheduleChecks(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean hasCommitments(UUID doctorId, Instant now) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from clinic.appointment a
                where (a.status = 'PENDING' and a.reserved_doctor_id = ?)
                   or (a.doctor_id = ? and
                       (a.status in ('ARRIVED', 'IN_PROGRESS') or
                        (a.status in ('CONFIRMED', 'COMPLETED') and clinic.plus_minutes(a.end_at, a.buffer_minutes) > ?))))
                """, Boolean.class, doctorId, doctorId, now.atOffset(ZoneOffset.UTC)));
    }

    public boolean inUse(DoctorBookingWindow window) {
        OffsetDateTime start = window.getDay().atTime(window.getStartTime()).atZone(ClinicTime.ZONE).toOffsetDateTime();
        OffsetDateTime end = window.getDay().atTime(window.getEndTime()).atZone(ClinicTime.ZONE).toOffsetDateTime();
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from clinic.appointment a where a.booking_window_id = ?
                or (a.doctor_id = ? and a.service_id = ?
                    and a.status in ('CONFIRMED', 'ARRIVED', 'IN_PROGRESS', 'COMPLETED')
                    and tstzrange(a.start_at, clinic.plus_minutes(a.end_at, a.buffer_minutes)) && tstzrange(?, ?)))
                """, Boolean.class, window.getId(), window.getDoctorId(), window.getServiceId(), start, end));
    }
}
