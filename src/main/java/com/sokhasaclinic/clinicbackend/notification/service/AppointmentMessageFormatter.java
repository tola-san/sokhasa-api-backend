package com.sokhasaclinic.clinicbackend.notification.service;

import com.sokhasaclinic.clinicbackend.appointment.service.AppointmentSnapshot;
import com.sokhasaclinic.clinicbackend.common.time.ClinicTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * The staff summary of a new booking, as Telegram HTML: the whole message in one block quote,
 * with bold labels, blank lines between groups, and the reference and phone in monospace so staff
 * can tap to copy them. Every value is escaped, so patient input cannot add formatting, links or
 * forged lines. The message never includes the patient's email or notes.
 */
@Component
public class AppointmentMessageFormatter {
    /** Messages that start with this are sent with Telegram's HTML parse mode; older queued jobs are plain text. */
    public static final String HTML_PREFIX = "<blockquote>";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter SUBMITTED = DateTimeFormatter
            .ofPattern("dd MMM uuuu, HH:mm", Locale.ENGLISH).withZone(ClinicTime.ZONE);

    public String created(AppointmentSnapshot appointment, String serviceName) {
        return created(appointment, serviceName, null);
    }

    /** {@code preferredDoctorName} is the patient's optional, non-binding choice (PD-07); null when none. */
    public String created(AppointmentSnapshot appointment, String serviceName, String preferredDoctorName) {
        String requestedTime = appointment.preferredStartTime() == null
                ? appointment.preferredPeriod() : TIME.format(appointment.preferredStartTime());
        StringBuilder message = new StringBuilder(HTML_PREFIX)
                .append("<b>New booking request</b>\n\n")
                .append(field("Reference", code(appointment.reference()))).append("\n\n")
                .append(field("Patient", text(appointment.patientName()))).append('\n')
                .append(field("Phone", code(appointment.patientPhone()))).append("\n\n")
                .append(field("Service", text(serviceName))).append('\n')
                .append(field("Requested date", text(DATE.format(appointment.preferredDate())))).append('\n')
                .append(field("Requested time", text(requestedTime)));
        if (preferredDoctorName != null && !preferredDoctorName.isBlank()) {
            message.append('\n').append(field("Preferred doctor", text(preferredDoctorName)));
        }
        return message.append("\n\n")
                .append(field("Status", "Pending review")).append('\n')
                .append(field("Submitted", text(SUBMITTED.format(appointment.createdAt()) + " (Asia/Phnom_Penh)")))
                .append("</blockquote>")
                .toString();
    }

    private static String field(String label, String value) {
        return "<b>" + label + ":</b>  " + value;
    }

    private static String code(String value) {
        return "<code>" + text(value) + "</code>";
    }

    /** One line of plain text: control and line-break characters become spaces, then HTML is escaped. */
    static String text(String value) {
        String line = value == null ? "" : value.replaceAll("[\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]", " ");
        return line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
