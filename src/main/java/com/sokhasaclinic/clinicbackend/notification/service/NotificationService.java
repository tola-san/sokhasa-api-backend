package com.sokhasaclinic.clinicbackend.notification.service;

import com.sokhasaclinic.clinicbackend.appointment.service.AppointmentSnapshot;
import com.sokhasaclinic.clinicbackend.notification.config.TelegramProperties;
import com.sokhasaclinic.clinicbackend.notification.repository.NotificationJobRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Booking events for Telegram: the staff summary of each new request, and the patient's opt-in
 * updates (PT-01 to PT-12). Called inside the booking or status-change transaction, so every message
 * is queued together with the change it describes.
 */
@Service
public class NotificationService {
    private final TelegramProperties properties;
    private final NotificationJobRepository jobs;
    private final AppointmentMessageFormatter formatter;
    private final PatientTelegramService patients;

    public NotificationService(TelegramProperties properties, NotificationJobRepository jobs,
            AppointmentMessageFormatter formatter, PatientTelegramService patients) {
        this.properties = properties;
        this.jobs = jobs;
        this.formatter = formatter;
        this.patients = patients;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void appointmentCreated(AppointmentSnapshot appointment) {
        patients.bookingCreated(appointment.id(), appointment.createdAt());
        if (!properties.isEnabled()) return;
        String name = jobs.serviceName(appointment.serviceId());
        String preferredDoctor = jobs.doctorName(appointment.preferredDoctorId());
        jobs.enqueue(appointment.id(), properties.getChatId(), formatter.created(appointment, name, preferredDoctor),
                appointment.createdAt());
    }

    /** PT-05: tells a linked patient that reception confirmed the request. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void appointmentConfirmed(UUID appointmentId) {
        patients.confirmed(appointmentId);
    }

    /** PT-05: tells a linked patient the request could not be confirmed, without the staff reason. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void appointmentRejected(UUID appointmentId) {
        patients.rejected(appointmentId);
    }
}
