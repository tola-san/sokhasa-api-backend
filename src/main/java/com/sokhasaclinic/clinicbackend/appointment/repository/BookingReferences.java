package com.sokhasaclinic.clinicbackend.appointment.repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Allocates booking references such as {@code SK-20261120-0007}: the preferred date and a per-date
 * counter (AP-01). The counter is an upsert, so concurrent requests for one date never share a number.
 */
@Component
public class BookingReferences {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final JdbcTemplate jdbc;

    public BookingReferences(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String next(LocalDate day) {
        Integer number = jdbc.queryForObject("""
                insert into clinic.appointment_reference_counter (day, last_value) values (?, 1)
                on conflict (day) do update set last_value = clinic.appointment_reference_counter.last_value + 1
                returning last_value
                """, Integer.class, day);
        return "SK-" + day.format(DAY) + "-" + String.format("%04d", number);
    }
}
