package com.sokhasaclinic.clinicbackend.notification.service;

import com.sokhasaclinic.clinicbackend.appointment.service.AppointmentSnapshot;
import com.sokhasaclinic.clinicbackend.notification.config.TelegramProperties;
import com.sokhasaclinic.clinicbackend.notification.repository.NotificationJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private final TelegramProperties properties;
    private final NotificationJobRepository jobs;
    private final AppointmentMessageFormatter formatter;

    public NotificationService(TelegramProperties properties, NotificationJobRepository jobs,
            AppointmentMessageFormatter formatter) {
        this.properties = properties;
        this.jobs = jobs;
        this.formatter = formatter;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void appointmentCreated(AppointmentSnapshot appointment) {
        if (!properties.isEnabled()) return;
        String name = jobs.serviceName(appointment.serviceId());
        String preferredDoctor = jobs.doctorName(appointment.preferredDoctorId());
        jobs.enqueue(appointment.id(), properties.getChatId(), formatter.created(appointment, name, preferredDoctor),
                appointment.createdAt());
    }
}
