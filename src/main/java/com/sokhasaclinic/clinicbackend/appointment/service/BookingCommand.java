package com.sokhasaclinic.clinicbackend.appointment.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * A guest's booking request as received: service, date and period (BK-01), the patient's language
 * and details (AP-05). {@link AppointmentService#request} validates and normalises it.
 */
public record BookingCommand(
        UUID serviceId,
        LocalDate preferredDate,
        String preferredPeriod,
        String languageCode,
        String fullName,
        String phone,
        String email,
        String notes,
        LocalTime preferredStartTime) {

    public BookingCommand(UUID serviceId, LocalDate preferredDate, String preferredPeriod, String languageCode,
            String fullName, String phone, String email, String notes) {
        this(serviceId, preferredDate, preferredPeriod, languageCode, fullName, phone, email, notes, null);
    }

    /** A request with only the required fields: English, no email and no notes. */
    public BookingCommand(UUID serviceId, LocalDate preferredDate, String preferredPeriod, String fullName,
            String phone) {
        this(serviceId, preferredDate, preferredPeriod, "en", fullName, phone, null, null);
    }
}
