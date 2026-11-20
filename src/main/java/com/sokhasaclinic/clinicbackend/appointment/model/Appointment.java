package com.sokhasaclinic.clinicbackend.appointment.model;

import com.sokhasaclinic.clinicbackend.appointment.exception.AppointmentException;
import com.sokhasaclinic.clinicbackend.clinicservice.model.ClinicService;
import com.sokhasaclinic.clinicbackend.doctor.model.Doctor;
import com.sokhasaclinic.clinicbackend.patient.model.Patient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import lombok.Getter;

/**
 * A booking request and its lifecycle. The patient's request ({@code preferredDate},
 * {@code preferredPeriod}) is never overwritten; the doctor and exact time are set on confirm
 * (decision BK-03). Every change goes through {@link AppointmentStatus#canMoveTo}, so there are
 * no setters.
 */
@Entity
@Table(name = "appointment", schema = "clinic")
@Getter
public class Appointment {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false, updatable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false, updatable = false)
    private ClinicService service;

    @Column(name = "patient_name", nullable = false, updatable = false, length = 120)
    private String patientName;

    @Column(name = "patient_phone", nullable = false, updatable = false, length = 16)
    private String patientPhone;

    /** AP-01: for example SK-20261120-0007. */
    @Column(nullable = false, updatable = false, unique = true, length = 20)
    private String reference;

    /** AP-05: km, en or zh, for later patient messages. */
    @Column(name = "language_code", nullable = false, updatable = false, length = 2)
    private String languageCode;

    @Column(name = "patient_email", updatable = false, length = 254)
    private String patientEmail;

    @Column(updatable = false, length = 500)
    private String notes;

    @Column(name = "preferred_date", nullable = false, updatable = false)
    private LocalDate preferredDate;

    @Column(name = "preferred_period", nullable = false, updatable = false, length = 20)
    private String preferredPeriod;

    @Column(name = "preferred_start_time", updatable = false)
    private LocalTime preferredStartTime;

    /** PD-03: the patient's optional, non-binding preferred doctor; never overwritten. */
    @Column(name = "preferred_doctor_id", updatable = false)
    private UUID preferredDoctorId;

    @Column(name = "booking_window_id", updatable = false)
    private UUID bookingWindowId;

    @Column(name = "reserved_doctor_id")
    private UUID reservedDoctorId;

    @Column(name = "reserved_start_at")
    private Instant reservedStartAt;

    @Column(name = "reserved_end_at")
    private Instant reservedEndAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "buffer_minutes", nullable = false, updatable = false)
    private int bufferMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private AppointmentStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id")
    private Doctor doctor;

    @Column(name = "start_at")
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Column(name = "confirmed_date")
    private LocalDate confirmedDate;

    @Column(name = "confirmed_period", length = 20)
    private String confirmedPeriod;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Appointment() {
    }

    /**
     * A new request in {@code PENDING}. Copies the service's duration and buffer (decision BK-07),
     * so a later service edit does not change this booking.
     */
    public static Appointment request(Patient patient, ClinicService service, String reference,
            PatientDetails details, LocalDate preferredDate, BookingPeriod preferredPeriod, Instant now) {
        Appointment appointment = new Appointment();
        appointment.id = UUID.randomUUID();
        appointment.patient = patient;
        appointment.service = service;
        appointment.reference = reference;
        appointment.patientName = details.fullName();
        appointment.patientPhone = details.phone();
        appointment.patientEmail = details.email();
        appointment.languageCode = details.languageCode();
        appointment.notes = details.notes();
        appointment.preferredDate = preferredDate;
        appointment.preferredPeriod = preferredPeriod.getCode();
        appointment.durationMinutes = service.getDurationMinutes();
        appointment.bufferMinutes = service.getBufferMinutes();
        appointment.status = AppointmentStatus.PENDING;
        appointment.createdAt = now;
        appointment.updatedAt = now;
        return appointment;
    }

    /**
     * {@code PENDING ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ CONFIRMED} with the doctor and exact time chosen by reception. The caller
     * has already checked that the appointment fits inside {@code period} on {@code date}.
     */
    public void reserve(UUID windowId, UUID doctorId, LocalTime requestedTime, Instant start) {
        if (status != AppointmentStatus.PENDING || preferredStartTime != null) {
            throw new IllegalStateException("Only a new pending request can reserve a slot.");
        }
        bookingWindowId = windowId;
        preferredStartTime = requestedTime;
        reservedDoctorId = doctorId;
        reservedStartAt = start;
        reservedEndAt = start.plusSeconds(durationMinutes * 60L);
    }

    /** PD-01: records the patient's preferred doctor on a new request, before it is first saved. */
    public void preferDoctor(UUID doctorId) {
        if (status != AppointmentStatus.PENDING || preferredDoctorId != null) {
            throw new IllegalStateException("Only a new pending request can record a preferred doctor.");
        }
        preferredDoctorId = doctorId;
    }

    private void clearReservation() {
        reservedDoctorId = null;
        reservedStartAt = null;
        reservedEndAt = null;
    }

    public void confirm(Doctor assignedDoctor, Instant start, int newDurationMinutes, LocalDate date,
            BookingPeriod period, Instant now) {
        moveTo(AppointmentStatus.CONFIRMED, now);
        clearReservation();
        doctor = assignedDoctor;
        durationMinutes = newDurationMinutes;
        startAt = start;
        endAt = start.plusSeconds(newDurationMinutes * 60L);
        confirmedDate = date;
        confirmedPeriod = period.getCode();
    }

    public void reject(Instant now) {
        moveTo(AppointmentStatus.REJECTED, now);
        clearReservation();
    }

    public void arrive(Instant now) {
        moveTo(AppointmentStatus.ARRIVED, now);
    }

    public void start(Instant now) {
        moveTo(AppointmentStatus.IN_PROGRESS, now);
    }

    public void complete(Instant now) {
        moveTo(AppointmentStatus.COMPLETED, now);
    }

    /** Minutes this appointment takes from its period's capacity: duration plus buffer. */
    public int capacityMinutes() {
        return durationMinutes + bufferMinutes;
    }

    private void moveTo(AppointmentStatus next, Instant now) {
        if (!status.canMoveTo(next)) {
            throw AppointmentException.invalidTransition(status, next);
        }
        status = next;
        updatedAt = now;
    }
}
