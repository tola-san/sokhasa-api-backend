package com.sokhasaclinic.clinicbackend.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sokhasaclinic.clinicbackend.appointment.model.AppointmentStatus;
import com.sokhasaclinic.clinicbackend.appointment.service.AppointmentSnapshot;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AppointmentMessageFormatterTests {

    private final AppointmentMessageFormatter formatter = new AppointmentMessageFormatter();

    private static AppointmentSnapshot booking(String patientName, LocalTime startTime) {
        Instant created = Instant.parse("2026-11-20T05:29:00Z"); // 12:29 in Phnom Penh
        return new AppointmentSnapshot(UUID.randomUUID(), "SK-20261120-0008", AppointmentStatus.PENDING,
                UUID.randomUUID(), patientName, "+85501234456", "private@example.com", "en", "PRIVATE NOTES",
                LocalDate.parse("2026-11-20"), "AFTERNOON", 30, 10, null, null, null, created, created, 0,
                startTime, null);
    }

    @Test
    void wholeSummaryIsOneSpacedBlockQuote() {
        String message = formatter.created(booking("Tola San", LocalTime.of(16, 0)),
                "High-Resolution Digital Ultrasound & Organ Imaging");
        assertEquals("""
                <blockquote><b>New booking request</b>

                <b>Reference:</b>  <code>SK-20261120-0008</code>

                <b>Patient:</b>  Tola San
                <b>Phone:</b>  <code>+85501234456</code>

                <b>Service:</b>  High-Resolution Digital Ultrasound &amp; Organ Imaging
                <b>Requested date:</b>  20 Nov 2026
                <b>Requested time:</b>  16:00

                <b>Status:</b>  Pending review
                <b>Submitted:</b>  20 Nov 2026, 12:29 (Asia/Phnom_Penh)</blockquote>""", message);
        assertTrue(message.startsWith(AppointmentMessageFormatter.HTML_PREFIX));
    }

    @Test
    void periodRequestsAndPreferredDoctorAreShown() {
        String message = formatter.created(booking("Tola San", null), "Colonoscopy", "Dr. Sok & Co");
        assertTrue(message.contains("<b>Requested time:</b>  AFTERNOON\n<b>Preferred doctor:</b>  Dr. Sok &amp; Co\n\n"));
    }

    @Test
    void patientInputCannotAddFormattingLinksOrForgedLines() {
        String message = formatter.created(
                booking("<b>Boss</b> <a href=\"http://x\">click</a>\n<b>Status:</b> Confirmed", LocalTime.of(9, 0)),
                "Colonoscopy");
        assertTrue(message.contains("&lt;b&gt;Boss&lt;/b&gt; &lt;a href=\"http://x\"&gt;click&lt;/a&gt;"));
        assertFalse(message.contains("<a "));
        assertEquals(1, message.split("<b>Status:</b>", -1).length - 1, "only the real status line");
        assertFalse(message.contains("PRIVATE NOTES"));
        assertFalse(message.contains("private@example.com"));
    }
}
