package com.sokhasaclinic.clinicbackend.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sokhasaclinic.clinicbackend.appointment.model.Actor;
import com.sokhasaclinic.clinicbackend.appointment.service.AppointmentQueryService;
import com.sokhasaclinic.clinicbackend.appointment.service.AppointmentService;
import com.sokhasaclinic.clinicbackend.appointment.service.AppointmentSnapshot;
import com.sokhasaclinic.clinicbackend.appointment.service.BookingCommand;
import com.sokhasaclinic.clinicbackend.appointment.service.ConfirmCommand;
import com.sokhasaclinic.clinicbackend.cms.service.storage.MediaStorage;
import com.sokhasaclinic.clinicbackend.common.time.ClinicTime;
import com.sokhasaclinic.clinicbackend.notification.client.TelegramClient;
import com.sokhasaclinic.clinicbackend.notification.service.PatientTelegramService;
import com.sokhasaclinic.clinicbackend.notification.service.PatientTelegramService.Chat;
import com.sokhasaclinic.clinicbackend.notification.service.PatientTelegramService.From;
import com.sokhasaclinic.clinicbackend.notification.service.PatientTelegramService.Message;
import com.sokhasaclinic.clinicbackend.notification.service.PatientTelegramService.Update;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/** Patient Telegram updates (PT-01 to PT-12) against PostgreSQL. Each test rolls back. */
@SpringBootTest(properties = {"scheduling.enabled=false", "clinic.telegram.enabled=true",
        "clinic.telegram.bot-token=123:test-token", "clinic.telegram.chat-id=-100123",
        "clinic.telegram.patient.enabled=true", "clinic.telegram.patient.bot-username=Sokhasaclinic_bot",
        "clinic.telegram.patient.webhook-secret=test-webhook-secret-0123456789abcdef",
        "clinic.telegram.patient.link-secret=test-link-secret-0123456789abcdefghij",
        "clinic.telegram.patient.clinic-name=C2G+ Sokhasa", "clinic.telegram.patient.clinic-address=Sen Sok, Phnom Penh",
        "clinic.telegram.patient.clinic-phone=095 666 990"})
@EnabledIfEnvironmentVariable(named = "CLINIC_TEST_DATABASE_URL", matches = "jdbc:postgresql:.+")
@Transactional
class PatientTelegramPostgresTests {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CLINIC_TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("CLINIC_TEST_DATABASE_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("CLINIC_TEST_DATABASE_PASSWORD"));
    }

    static final long CHAT = 8878232559L;
    static final long OTHER_CHAT = 7000000001L;

    @MockitoBean MediaStorage mediaStorage;
    @MockitoBean TelegramClient telegram;
    @Autowired AppointmentService appointments;
    @Autowired AppointmentQueryService queries;
    @Autowired PatientTelegramService patients;
    @Autowired JdbcTemplate jdbc;

    UUID service;
    UUID doctor;

    @BeforeEach
    void setUp() {
        service = jdbc.queryForObject("select id from clinic.service where slug = 'chronic-disease-management'", UUID.class);
        doctor = UUID.randomUUID();
        jdbc.update("insert into clinic.doctor(id, full_name) values (?, 'Dr. Telegram')", doctor);
    }

    AppointmentSnapshot book(LocalDate date, String language) {
        return appointments.request(new BookingCommand(service, date, "MORNING", language, "Sok <b>Dara</b>",
                "012 345 678", "private@example.com", "PRIVATE MEDICAL NOTES"));
    }

    AppointmentSnapshot book() {
        return book(LocalDate.now(ClinicTime.ZONE).plusDays(20), "en");
    }

    String code(UUID appointmentId) {
        String url = patients.linkUrl(appointmentId);
        assertTrue(url.startsWith("https://t.me/Sokhasaclinic_bot?start="), url);
        return url.substring(url.indexOf("=") + 1);
    }

    void send(long chat, String type, String text) {
        patients.handle(new Update(1L, new Message(new Chat(chat, type), new From("en"), text)));
    }

    List<Map<String, Object>> jobs(String eventType) {
        return jdbc.queryForList("""
                select appointment_id, chat_id, message, recipient, subscription_id, status
                from clinic.notification_job where event_type = ? order by created_at, id
                """, eventType);
    }

    List<Map<String, Object>> replies(long chat) {
        return jdbc.queryForList("select message from clinic.notification_job where event_type = 'PATIENT_REPLY' "
                + "and chat_id = ? order by created_at, id", Long.toString(chat));
    }

    UUID subscription(UUID appointmentId) {
        return jdbc.queryForObject("select id from clinic.telegram_subscription where appointment_id = ?",
                UUID.class, appointmentId);
    }

    @Test
    void bookingGetsAStableSingleUseLink() {
        var booked = book();
        String code = code(booked.id());
        assertEquals(43, code.length());
        assertEquals(code, code(booked.id()), "an idempotent replay returns the same link");
        assertEquals(1, jdbc.queryForObject("select count(*) from clinic.appointment_telegram_link "
                + "where appointment_id = ? and used_at is null", Integer.class, booked.id()));
        String stored = jdbc.queryForObject("select code_hash from clinic.appointment_telegram_link "
                + "where appointment_id = ?", String.class, booked.id());
        assertFalse(stored.contains(code), "only the hash is stored");
    }

    @Test
    void startLinksPrivateChatAndConfirmsConnection() {
        var booked = book();
        send(CHAT, "private", "/start " + code(booked.id()));
        assertNotNull(subscription(booked.id()));
        var linked = jobs("PATIENT_LINKED");
        assertEquals(1, linked.size());
        assertEquals(Long.toString(CHAT), linked.getFirst().get("chat_id"));
        assertEquals("PATIENT", linked.getFirst().get("recipient"));
        String message = (String) linked.getFirst().get("message");
        assertTrue(message.startsWith("<blockquote><b>You're connected</b>"), message);
        assertTrue(message.contains(booked.reference()));
        assertTrue(queries.detail(booked.id()).data().patientTelegramConnected());
    }

    @Test
    void sameChatAgainIsIdempotentAndASecondChatIsRefused() {
        var booked = book();
        String code = code(booked.id());
        send(CHAT, "private", "/start " + code);
        send(CHAT, "private", "/start " + code);
        send(OTHER_CHAT, "private", "/start " + code);
        assertEquals(1, jdbc.queryForObject("select count(*) from clinic.telegram_subscription "
                + "where appointment_id = ?", Integer.class, booked.id()));
        assertTrue(((String) replies(CHAT).getFirst().get("message")).contains("You're connected"));
        assertTrue(((String) replies(OTHER_CHAT).getFirst().get("message")).contains("already connected"));
    }

    @Test
    void groupsExpiredAndUnknownCodesNeverLink() {
        var booked = book();
        String code = code(booked.id());
        send(-5565238118L, "group", "/start " + code);
        assertEquals(0, jdbc.queryForObject("select count(*) from clinic.notification_job where chat_id = '-5565238118'",
                Integer.class));
        send(CHAT, "private", "/start " + "x".repeat(43));
        assertTrue(((String) replies(CHAT).getLast().get("message")).contains("not valid or has expired"));
        jdbc.update("update clinic.appointment_telegram_link set expires_at = now() - interval '1 hour' "
                + "where appointment_id = ?", booked.id());
        send(CHAT, "private", "/start " + code);
        assertEquals(0, jdbc.queryForObject("select count(*) from clinic.telegram_subscription "
                + "where appointment_id = ?", Integer.class, booked.id()));
        assertEquals(2, replies(CHAT).size());
    }

    @Test
    void confirmTellsALinkedPatientWithDoctorAndTime() {
        var booked = book();
        send(CHAT, "private", "/start " + code(booked.id()));
        appointments.confirm(booked.id(), booked.version(),
                new ConfirmCommand(doctor, booked.preferredDate(), LocalTime.of(9, 0), null), Actor.staff("reception"));
        var confirmed = jobs("PATIENT_CONFIRMED");
        assertEquals(1, confirmed.size());
        assertEquals(subscription(booked.id()), confirmed.getFirst().get("subscription_id"));
        String message = (String) confirmed.getFirst().get("message");
        assertTrue(message.contains("Your appointment is confirmed"), message);
        assertTrue(message.contains("Dr. Telegram"));
        assertTrue(message.contains("<b>Time:</b>  09:00 – "), message);
        assertTrue(message.contains("<code>095 666 990</code>"));
        assertFalse(message.contains("PRIVATE MEDICAL NOTES"));
        assertFalse(message.contains("private@example.com"));
    }

    @Test
    void rejectionNeverIncludesTheStaffReason() {
        var booked = book();
        send(CHAT, "private", "/start " + code(booked.id()));
        appointments.reject(booked.id(), booked.version(), "Doctor on leave", Actor.staff("reception"));
        String message = (String) jobs("PATIENT_REJECTED").getFirst().get("message");
        assertTrue(message.contains("We couldn't confirm your request"), message);
        assertFalse(message.contains("Doctor on leave"));
    }

    @Test
    void unlinkedBookingsGetNoPatientMessages() {
        var booked = book();
        appointments.confirm(booked.id(), booked.version(),
                new ConfirmCommand(doctor, booked.preferredDate(), LocalTime.of(9, 0), null), Actor.staff("reception"));
        assertTrue(jobs("PATIENT_CONFIRMED").isEmpty());
        assertFalse(queries.detail(booked.id()).data().patientTelegramConnected());
    }

    @Test
    void stopRevokesSoLaterChangesSendNothing() {
        var booked = book();
        send(CHAT, "private", "/start " + code(booked.id()));
        UUID subscription = subscription(booked.id());
        send(CHAT, "private", "/stop");
        assertTrue(((String) replies(CHAT).getLast().get("message")).contains("Updates are turned off"));
        assertFalse(queries.detail(booked.id()).data().patientTelegramConnected());
        assertNotNull(jdbc.queryForObject("select revoked_at from clinic.telegram_subscription where id = ?",
                java.time.OffsetDateTime.class, subscription));
        appointments.confirm(booked.id(), booked.version(),
                new ConfirmCommand(doctor, booked.preferredDate(), LocalTime.of(9, 0), null), Actor.staff("reception"));
        assertTrue(jobs("PATIENT_CONFIRMED").isEmpty());
        send(CHAT, "private", "/stop");
        assertTrue(((String) replies(CHAT).getLast().get("message")).contains("no active updates"));
        // A message queued before /stop is cancelled at delivery: NotificationWorkerTests.
    }

    @Test
    void reminderIsQueuedOnceForTomorrowsConfirmedVisit() {
        LocalDate tomorrow = LocalDate.now(ClinicTime.ZONE).plusDays(1);
        var booked = book(tomorrow, "km");
        send(CHAT, "private", "/start " + code(booked.id()));
        appointments.confirm(booked.id(), booked.version(),
                new ConfirmCommand(doctor, tomorrow, LocalTime.of(10, 0), null), Actor.staff("reception"));
        int before = jobs("PATIENT_REMINDER").size();
        patients.queueReminders(tomorrow);
        patients.queueReminders(tomorrow);
        var reminders = jobs("PATIENT_REMINDER");
        assertEquals(before + 1, reminders.size(), "at most one reminder per appointment");
        String message = (String) reminders.getLast().get("message");
        assertTrue(message.contains("រំលឹក"), "Khmer booking gets a Khmer reminder: " + message);
    }
}
