package com.sokhasaclinic.clinicbackend.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sokhasaclinic.clinicbackend.notification.config.TelegramProperties;
import com.sokhasaclinic.clinicbackend.notification.repository.PatientTelegramRepository.BookingView;
import com.sokhasaclinic.clinicbackend.notification.service.PatientMessageFormatter.Reply;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PatientMessageFormatterTests {

    private final PatientMessageFormatter formatter;

    PatientMessageFormatterTests() {
        TelegramProperties properties = new TelegramProperties();
        properties.getPatient().setClinicName("C2G+ Sokhasa");
        properties.getPatient().setClinicAddress("Sen Sok, Phnom Penh");
        properties.getPatient().setClinicPhone("095 666 990");
        formatter = new PatientMessageFormatter(properties);
    }

    private static BookingView booking(String language, String serviceName) {
        return new BookingView(UUID.randomUUID(), "SK-20261120-0008", "CONFIRMED", language, serviceName,
                LocalDate.parse("2026-11-20"), LocalTime.of(16, 0), "AFTERNOON",
                Instant.parse("2026-11-20T09:00:00Z"), Instant.parse("2026-11-20T09:30:00Z"), "Dr. Sok");
    }

    @Test
    void confirmedMatchesTheApprovedSample() {
        String message = formatter.confirmed(booking("en", "High-Resolution Digital Ultrasound & Organ Imaging"));
        assertEquals("""
                <blockquote><b>Your appointment is confirmed</b>

                <b>Reference:</b>  <code>SK-20261120-0008</code>

                <b>Service:</b>  High-Resolution Digital Ultrasound &amp; Organ Imaging
                <b>Date:</b>  Fri, 20 Nov 2026
                <b>Time:</b>  16:00 – 16:30
                <b>Doctor:</b>  Dr. Sok

                <b>Clinic:</b>  C2G+ Sokhasa, Sen Sok, Phnom Penh
                <b>Phone:</b>  <code>095 666 990</code></blockquote>

                Please arrive 10 minutes early.""", message);
    }

    @Test
    void linkedShowsTheRequestAndHowToStop() {
        String message = formatter.linked(booking("en", "Colonoscopy"));
        assertTrue(message.startsWith("<blockquote><b>You're connected</b>"));
        assertTrue(message.contains("<b>Requested:</b>  Fri, 20 Nov 2026, 16:00"));
        assertTrue(message.contains("Waiting for reception to confirm"));
        assertTrue(message.endsWith("Send /stop any time to turn off updates."));
    }

    @Test
    void rejectedGivesTheClinicPhoneOnly() {
        String message = formatter.rejected(booking("en", "Colonoscopy"));
        assertTrue(message.contains("We couldn't confirm your request"));
        assertTrue(message.contains("call us on 095 666 990"));
    }

    @Test
    void bookingLanguageIsUsedAndUnknownLanguagesFallBackToEnglish() {
        assertTrue(formatter.confirmed(booking("km", "Colonoscopy")).contains("ការណាត់ជួបរបស់អ្នកត្រូវបានបញ្ជាក់"));
        assertTrue(formatter.confirmed(booking("zh", "Colonoscopy")).contains("您的预约已确认"));
        assertTrue(formatter.reminder(booking("zh", "Colonoscopy")).contains("提醒"));
        assertTrue(formatter.reply(Reply.STOPPED, "fr").contains("Updates are turned off"));
        assertTrue(formatter.reply(Reply.INVALID_LINK, "km").contains("095 666 990"));
    }

    @Test
    void valuesAreEscaped() {
        String message = formatter.linked(booking("en", "<a href=\"http://x\">Free</a>\n<b>Status:</b> Confirmed"));
        assertFalse(message.contains("<a "));
        assertTrue(message.contains("&lt;a href=\"http://x\"&gt;Free&lt;/a&gt;"));
        assertEquals(1, message.split("<b>Status:</b>", -1).length - 1, "only the real status line");
    }
}
