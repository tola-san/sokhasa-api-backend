package com.sokhasaclinic.clinicbackend.appointment.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Booking settings the clinic can change without a deploy (the single booking_setting row). */
@Component
public class BookingSettings {

    private final JdbcTemplate jdbc;

    public BookingSettings(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** AP-02: patients may request today up to today + this many days. */
    public int windowDays() {
        Integer days = jdbc.queryForObject("select window_days from clinic.booking_setting where id = 1", Integer.class);
        return days == null ? 60 : days;
    }
}
