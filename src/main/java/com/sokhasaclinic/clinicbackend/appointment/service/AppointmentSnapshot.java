package com.sokhasaclinic.clinicbackend.appointment.service;

import com.sokhasaclinic.clinicbackend.appointment.model.Appointment;
import com.sokhasaclinic.clinicbackend.appointment.model.AppointmentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Read-only view of an appointment returned by {@link AppointmentService}, so the JPA entity never
 * leaves the service layer. Controllers map it to the response DTOs.
 */
public record AppointmentSnapshot(
        UUID id,
        String reference,
        AppointmentStatus status,
        UUID serviceId,
        String patientName,
        String patientPhone,
        String patientEmail,
        String languageCode,
        String notes,
        LocalDate preferredDate,
        String preferredPeriod,
        int durationMinutes,
        int bufferMinutes,
        UUID doctorId,
        Instant startAt,
        Instant endAt,
        Instant createdAt,
        Instant updatedAt,
        long version,
        LocalTime preferredStartTime) {

    static AppointmentSnapshot of(Appointment a) {
        return new AppointmentSnapshot(
                a.getId(),
                a.getReference(),
                a.getStatus(),
                a.getService().getId(),
                a.getPatientName(),
                a.getPatientPhone(),
                a.getPatientEmail(),
                a.getLanguageCode(),
                a.getNotes(),
                a.getPreferredDate(),
                a.getPreferredPeriod(),
                a.getDurationMinutes(),
                a.getBufferMinutes(),
                a.getDoctor() == null ? null : a.getDoctor().getId(),
                a.getStartAt(),
                a.getEndAt(),
                a.getCreatedAt(),
                a.getUpdatedAt(),
                a.getVersion(),
                a.getPreferredStartTime());
    }
}
