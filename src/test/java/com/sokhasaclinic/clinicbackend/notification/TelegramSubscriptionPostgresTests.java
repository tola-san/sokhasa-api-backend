package com.sokhasaclinic.clinicbackend.notification;

import static org.junit.jupiter.api.Assertions.*;

import com.sokhasaclinic.clinicbackend.appointment.service.AppointmentService;
import com.sokhasaclinic.clinicbackend.appointment.service.BookingCommand;
import com.sokhasaclinic.clinicbackend.common.time.ClinicTime;
import com.sokhasaclinic.clinicbackend.notification.model.TelegramSubscription;
import com.sokhasaclinic.clinicbackend.notification.repository.TelegramSubscriptionRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {"scheduling.enabled=false", "clinic.telegram.enabled=false"})
@EnabledIfEnvironmentVariable(named = "CLINIC_TEST_DATABASE_URL", matches = "jdbc:postgresql:.+")
@Transactional
class TelegramSubscriptionPostgresTests {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CLINIC_TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("CLINIC_TEST_DATABASE_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("CLINIC_TEST_DATABASE_PASSWORD"));
    }

    @Autowired AppointmentService appointments;
    @Autowired TelegramSubscriptionRepository subscriptions;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entities;

    private UUID book() {
        UUID service = jdbc.queryForObject("select id from clinic.service where slug = 'chronic-disease-management'", UUID.class);
        return appointments.request(new BookingCommand(service, LocalDate.now(ClinicTime.ZONE).plusDays(31),
                "MORNING", "Subscription Test", "012345678")).id();
    }

    @Test
    void storesPrivateChatAndConsentThenExcludesRevokedSubscriptionFromActiveLookups() {
        UUID booking = book();
        long chat = 1234567890123L;
        Instant consent = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var subscription = subscriptions.saveAndFlush(TelegramSubscription.subscribe(booking, chat, consent));
        UUID id = subscription.getId();
        entities.clear();
        var loaded = subscriptions.findByAppointmentIdAndRevokedAtIsNull(booking).orElseThrow();
        assertEquals(chat, loaded.getChatId());
        assertEquals(consent, loaded.getConsentedAt());
        assertEquals(1, subscriptions.findAllByChatIdAndRevokedAtIsNull(chat).size());
        loaded.revoke(consent.plusSeconds(10));
        subscriptions.flush();
        entities.clear();
        assertTrue(subscriptions.findByAppointmentIdAndRevokedAtIsNull(booking).isEmpty());
        assertTrue(subscriptions.findAllByChatIdAndRevokedAtIsNull(chat).isEmpty());
        assertEquals(id, subscriptions.findByAppointmentId(booking).orElseThrow().getId());
    }

    @Test
    void cannotCreateASecondSubscriptionOrReplaceRecipientForTheSameBooking() {
        UUID booking = book();
        subscriptions.saveAndFlush(TelegramSubscription.subscribe(booking, 123L, Instant.now()));
        assertThrows(DataIntegrityViolationException.class, () -> subscriptions.saveAndFlush(
                TelegramSubscription.subscribe(booking, 456L, Instant.now())));
    }

    @Test
    void oneChatCanSubscribeToMultipleBookings() {
        subscriptions.saveAndFlush(TelegramSubscription.subscribe(book(), 123L, Instant.now()));
        subscriptions.saveAndFlush(TelegramSubscription.subscribe(book(), 123L, Instant.now()));
        assertEquals(2, subscriptions.findAllByChatIdAndRevokedAtIsNull(123L).size());
    }

    @Test
    void nonexistentBookingIsRejectedByTheDatabase() {
        assertThrows(DataIntegrityViolationException.class, () -> subscriptions.saveAndFlush(
                TelegramSubscription.subscribe(UUID.randomUUID(), 123L, Instant.now())));
    }

    @Test
    void staleSubscriptionUpdateIsRejected() {
        var subscription = subscriptions.saveAndFlush(TelegramSubscription.subscribe(book(), 123L, Instant.now()));
        jdbc.update("update clinic.telegram_subscription set version = version + 1 where id = ?", subscription.getId());
        subscription.revoke(Instant.now());
        assertThrows(OptimisticLockingFailureException.class, subscriptions::flush);
    }
}
