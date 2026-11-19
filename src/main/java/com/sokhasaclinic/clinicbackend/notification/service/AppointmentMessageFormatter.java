package com.sokhasaclinic.clinicbackend.notification.service;

import com.sokhasaclinic.clinicbackend.appointment.service.AppointmentSnapshot;
import com.sokhasaclinic.clinicbackend.common.time.ClinicTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class AppointmentMessageFormatter {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter SUBMITTED = DateTimeFormatter
            .ofPattern("dd MMM uuuu, HH:mm", Locale.ENGLISH).withZone(ClinicTime.ZONE);

    public String created(AppointmentSnapshot appointment, String serviceName) {
        String requestedTime = appointment.preferredStartTime() == null
                ? appointment.preferredPeriod() : TIME.format(appointment.preferredStartTime());
        return """
                New booking request
                Reference: %s
                Patient: %s
                Phone: %s
                Service: %s
                Requested date: %s
                Requested time: %s
                Status: Pending review
                Submitted: %s (Asia/Phnom_Penh)""".formatted(
                line(appointment.reference()), line(appointment.patientName()), line(appointment.patientPhone()),
                line(serviceName), DATE.format(appointment.preferredDate()), line(requestedTime),
                SUBMITTED.format(appointment.createdAt()));
    }

    // Plain text only: user input cannot add forged fields or Telegram formatting.
    private static String line(String value) {
        return value.replaceAll("[\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]", " ");
    }
}
