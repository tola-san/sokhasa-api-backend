package com.sokhasaclinic.clinicbackend.notification;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sokhasaclinic.clinicbackend.appointment.service.AppointmentService;
import com.sokhasaclinic.clinicbackend.appointment.service.AppointmentSnapshot;
import com.sokhasaclinic.clinicbackend.appointment.service.BookingCommand;
import com.sokhasaclinic.clinicbackend.common.time.ClinicTime;
import com.sokhasaclinic.clinicbackend.notification.client.TelegramClient;
import com.sokhasaclinic.clinicbackend.notification.config.TelegramProperties;
import com.sokhasaclinic.clinicbackend.notification.repository.NotificationJobRepository;
import com.sokhasaclinic.clinicbackend.notification.repository.NotificationJobRepository.Job;
import com.sokhasaclinic.clinicbackend.notification.worker.NotificationWorker;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {"scheduling.enabled=false", "clinic.telegram.enabled=true",
        "clinic.telegram.bot-token=123:test-token", "clinic.telegram.chat-id=-100123"})
@EnabledIfEnvironmentVariable(named = "CLINIC_TEST_DATABASE_URL", matches = "jdbc:postgresql:.+")
class NotificationPostgresTests {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CLINIC_TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("CLINIC_TEST_DATABASE_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("CLINIC_TEST_DATABASE_PASSWORD"));
    }

    @Autowired AppointmentService appointments;
    @Autowired NotificationJobRepository jobs;
    @Autowired NotificationWorker worker;
    @Autowired TelegramProperties properties;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean TelegramClient telegram;
    private final List<UUID> ids = new ArrayList<>();

    @AfterEach
    void cleanup() {
        properties.setEnabled(true);
        for (UUID id : ids) {
            jdbc.update("delete from clinic.notification_job where appointment_id = ?", id);
            jdbc.update("delete from clinic.appointment where id = ?", id);
        }
    }

    private BookingCommand command() {
        UUID service = jdbc.queryForObject("select id from clinic.service where slug = 'chronic-disease-management'", UUID.class);
        return new BookingCommand(service, LocalDate.now(ClinicTime.ZONE).plusDays(29), "MORNING", "en",
                "Sok Dara\nStatus: Confirmed", "012345678", "private@example.com", "PRIVATE MEDICAL NOTES");
    }

    private AppointmentSnapshot book() {
        var appointment = appointments.request(command());
        ids.add(appointment.id());
        return appointment;
    }

    @Test
    void bookingAndIdempotentReplayCreateOneSnapshotSummary() {
        UUID key = UUID.randomUUID();
        var first = appointments.request(command(), key).appointment();
        ids.add(first.id());
        assertTrue(appointments.request(command(), key).replayed());
        assertEquals(1, count(first.id()));
        String message = jdbc.queryForObject("select message from clinic.notification_job where appointment_id = ?",
                String.class, first.id());
        assertTrue(message.contains("Phone: +85512345678"));
        assertTrue(message.contains("Status: Pending review"));
        assertTrue(message.contains("Asia/Phnom_Penh"));
        assertFalse(message.contains("PRIVATE MEDICAL NOTES"));
        assertFalse(message.contains("private@example.com"));
        assertFalse(message.contains("\nStatus: Confirmed"));
        assertFalse(message.contains("http"));
    }

    @Test
    void rollbackRemovesAppointmentAndNotificationTogether() {
        UUID id = new TransactionTemplate(transactions).execute(status -> {
            var booked = book();
            assertEquals(1, count(booked.id()));
            status.setRollbackOnly();
            return booked.id();
        });
        assertEquals(0, count(id));
        assertEquals(0, jdbc.queryForObject("select count(*) from clinic.appointment where id = ?", Integer.class, id));
        verifyNoInteractions(telegram);
    }

    @Test
    void disabledSkipsOutboxAndNetwork() {
        properties.setEnabled(false);
        var appointment = book();
        assertEquals(0, count(appointment.id()));
        worker.deliver();
        verifyNoInteractions(telegram);
    }

    @Test
    void concurrentWorkersClaimDifferentJobsAndLeasesRejectStaleCompletion() throws Exception {
        book();
        book();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> jobs.claim(5).orElseThrow());
            var b = executor.submit(() -> jobs.claim(5).orElseThrow());
            Job first = a.get();
            Job second = b.get();
            assertNotEquals(first.id(), second.id());
            assertTrue(jobs.claim(5).isEmpty());
            jdbc.update("update clinic.notification_job set lease_until = now() - interval '1 second' where id = ?", first.id());
            Job recovered = jobs.claim(5).orElseThrow();
            assertEquals(first.id(), recovered.id());
            assertEquals(2, recovered.attempts());
            assertFalse(jobs.sent(first));
            assertTrue(jobs.sent(recovered));
        }
    }

    @Test
    void transientFailureKeepsBookingAndRetriesOnlyWhenDue() {
        var appointment = book();
        doThrow(new TelegramClient.DeliveryException("TELEGRAM_500", true, 0))
                .when(telegram).send(anyString(), anyString());
        worker.deliver();
        assertEquals("PENDING", state(appointment.id()));
        assertTrue(jobs.claim(5).isEmpty());
        reset(telegram);
        jdbc.update("update clinic.notification_job set next_attempt_at = now() - interval '1 second' where appointment_id = ?", appointment.id());
        worker.deliver();
        assertEquals("SENT", state(appointment.id()));
        worker.deliver();
        verify(telegram, times(1)).send(eq("-100123"), anyString());
        assertEquals("PENDING", jdbc.queryForObject("select status from clinic.appointment where id = ?", String.class, appointment.id()));
    }

    @Test
    void expiredFinalLeaseBecomesFailed() {
        var appointment = book();
        jobs.claim(5).orElseThrow();
        jdbc.update("update clinic.notification_job set attempts = 5, lease_until = now() - interval '1 second' where appointment_id = ?", appointment.id());
        assertTrue(jobs.claim(5).isEmpty());
        assertEquals("FAILED", state(appointment.id()));
    }

    private int count(UUID id) {
        return jdbc.queryForObject("select count(*) from clinic.notification_job where appointment_id = ?", Integer.class, id);
    }

    private String state(UUID id) {
        return jdbc.queryForObject("select status from clinic.notification_job where appointment_id = ?", String.class, id);
    }
}
