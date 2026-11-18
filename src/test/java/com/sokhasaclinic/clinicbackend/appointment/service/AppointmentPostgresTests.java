package com.sokhasaclinic.clinicbackend.appointment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sokhasaclinic.clinicbackend.appointment.exception.AppointmentException;
import com.sokhasaclinic.clinicbackend.appointment.model.Actor;
import com.sokhasaclinic.clinicbackend.appointment.model.ActorType;
import com.sokhasaclinic.clinicbackend.appointment.model.AppointmentStatus;
import com.sokhasaclinic.clinicbackend.appointment.repository.AppointmentRepository;
import com.sokhasaclinic.clinicbackend.cms.service.storage.MediaStorage;
import com.sokhasaclinic.clinicbackend.common.time.ClinicTime;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
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

/**
 * The approved booking-period and confirm acceptance cases against a real PostgreSQL database. Set
 * CLINIC_TEST_DATABASE_URL (and _USERNAME / _PASSWORD) to a disposable database to enable it. Each
 * test rolls back. Concurrency is covered by {@link AppointmentConcurrencyPostgresTests}.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@EnabledIfEnvironmentVariable(named = "CLINIC_TEST_DATABASE_URL", matches = "jdbc:postgresql:.+")
@Transactional
class AppointmentPostgresTests {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CLINIC_TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("CLINIC_TEST_DATABASE_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("CLINIC_TEST_DATABASE_PASSWORD"));
    }

    private static final Actor RECEPTION = Actor.staff("reception-1");

    @MockitoBean
    MediaStorage mediaStorage;

    @Autowired
    AppointmentService appointments;

    @Autowired
    AppointmentRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager entityManager;

    /** 30 + 10 minutes, the service in the acceptance cases. */
    private UUID service;
    /** 50 + 0 minutes, used to reach 450 used minutes. */
    private UUID fiftyMinuteService;
    private UUID doctor;
    private LocalDate date;
    private int phoneCounter;

    @BeforeEach
    void setUp() {
        service = jdbc.queryForObject(
                "select id from clinic.service where slug = 'chronic-disease-management'", UUID.class);
        fiftyMinuteService = jdbc.queryForObject(
                "select id from clinic.service where slug = 'high-resolution-ultrasound'", UUID.class);
        jdbc.update("update clinic.service set duration_minutes = 30, buffer_minutes = 10 where id = ?", service);
        jdbc.update("update clinic.service set duration_minutes = 50, buffer_minutes = 0 where id = ?",
                fiftyMinuteService);
        doctor = insertDoctor("Dr. Test", "ACTIVE");
        date = LocalDate.now(ClinicTime.ZONE).plusDays(14);
    }

    @Test
    void requestStartsPendingWithCopiedDurationAndHistory() {
        AppointmentSnapshot booked = book(service, "MORNING");

        assertEquals(AppointmentStatus.PENDING, booked.status());
        assertEquals(30, booked.durationMinutes());
        assertEquals(10, booked.bufferMinutes());
        assertNull(booked.doctorId());
        List<StatusChange> history = appointments.history(booked.id());
        assertEquals(1, history.size());
        assertNull(history.getFirst().fromStatus());
        assertEquals(AppointmentStatus.PENDING, history.getFirst().toStatus());
        assertEquals(ActorType.GUEST, history.getFirst().actorType());
    }

    @Test
    void requestFitsWhen440Of480MinutesAreUsed() {
        for (int i = 0; i < 11; i++) {
            book(service, "MORNING");
        }
        assertEquals(440, repository.minutesUsed(date, "MORNING"));

        assertEquals(AppointmentStatus.PENDING, book(service, "MORNING").status());
        assertEquals(480, repository.minutesUsed(date, "MORNING"));
    }

    @Test
    void requestIsRejectedWhen450Of480MinutesAreUsed() {
        for (int i = 0; i < 10; i++) {
            book(service, "MORNING");
        }
        book(fiftyMinuteService, "MORNING");
        assertEquals(450, repository.minutesUsed(date, "MORNING"));

        AppointmentException full = assertThrows(AppointmentException.class, () -> book(service, "MORNING"));

        assertEquals("PERIOD_FULL", full.getType());
        assertEquals(409, full.getStatus());
        assertEquals(450, repository.minutesUsed(date, "MORNING"));
    }

    @Test
    void rejectingFreesTheMinutes() {
        AppointmentSnapshot booked = book(service, "MORNING");

        AppointmentSnapshot rejected = appointments.reject(booked.id(), booked.version(), "  Fully booked  ", RECEPTION);

        assertEquals(AppointmentStatus.REJECTED, rejected.status());
        assertEquals(0, repository.minutesUsed(date, "MORNING"));
        assertEquals("Fully booked", appointments.history(booked.id()).getLast().reason());
    }

    @Test
    void rejectNeedsAReason() {
        AppointmentSnapshot booked = book(service, "MORNING");

        AppointmentException invalid = assertThrows(AppointmentException.class,
                () -> appointments.reject(booked.id(), booked.version(), "   ", RECEPTION));

        assertEquals("VALIDATION_ERROR", invalid.getType());
    }

    @Test
    void confirmPastPeriodCloseIsRejected() {
        AppointmentSnapshot booked = book(service, "MORNING");

        AppointmentException invalid = assertThrows(AppointmentException.class,
                () -> confirm(booked, LocalTime.of(11, 40), null));

        assertEquals(400, invalid.getStatus());
        assertEquals("startTime", invalid.getErrors().getFirst().get("field"));
    }

    @Test
    void confirmSetsDoctorAndTimesInClinicTime() {
        AppointmentSnapshot booked = book(service, "MORNING");

        AppointmentSnapshot confirmed = confirm(booked, LocalTime.of(9, 30), null);

        assertEquals(AppointmentStatus.CONFIRMED, confirmed.status());
        assertEquals(doctor, confirmed.doctorId());
        assertEquals(LocalDateTime.of(date, LocalTime.of(9, 30)).atZone(ClinicTime.ZONE).toInstant(),
                confirmed.startAt());
        assertEquals(confirmed.startAt().plusSeconds(30 * 60), confirmed.endAt());
        assertEquals(booked.version() + 1, confirmed.version());
    }

    @Test
    void confirmCanChangeTheDuration() {
        AppointmentSnapshot booked = book(service, "MORNING");

        AppointmentSnapshot confirmed = confirm(booked, LocalTime.of(9, 0), 45);

        assertEquals(45, confirmed.durationMinutes());
        assertEquals(confirmed.startAt().plusSeconds(45 * 60), confirmed.endAt());
    }

    @Test
    void serviceEditDoesNotChangeExistingBookings() {
        AppointmentSnapshot booked = book(service, "MORNING");

        jdbc.update("update clinic.service set duration_minutes = 45 where id = ?", service);
        entityManager.clear();

        assertEquals(30, appointments.get(booked.id()).durationMinutes());
    }

    @Test
    void morningRequestConfirmedInTheAfternoonCountsAgainstTheAfternoon() {
        AppointmentSnapshot booked = book(service, "MORNING");

        AppointmentSnapshot confirmed = confirm(booked, LocalTime.of(14, 0), null);

        assertEquals("MORNING", confirmed.preferredPeriod());
        assertEquals(0, repository.minutesUsed(date, "MORNING"));
        assertEquals(40, repository.minutesUsed(date, "AFTERNOON"));
    }

    @Test
    void visitMovesThroughEveryStepWithHistory() {
        AppointmentSnapshot booked = book(service, "MORNING");
        AppointmentSnapshot confirmed = confirm(booked, LocalTime.of(10, 0), null);
        AppointmentSnapshot arrived = appointments.arrive(booked.id(), confirmed.version(), RECEPTION);
        AppointmentSnapshot started = appointments.start(booked.id(), arrived.version(), RECEPTION);
        AppointmentSnapshot completed = appointments.complete(booked.id(), started.version(), RECEPTION);

        assertEquals(AppointmentStatus.COMPLETED, completed.status());
        assertEquals(List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED, AppointmentStatus.ARRIVED,
                        AppointmentStatus.IN_PROGRESS, AppointmentStatus.COMPLETED),
                appointments.history(booked.id()).stream().map(StatusChange::toStatus).toList());
        // A completed visit still counts against its period: only REJECTED requests are freed.
        assertEquals(40, repository.minutesUsed(date, "MORNING"));
    }

    @Test
    void skippingAStepIsAnInvalidTransition() {
        AppointmentSnapshot booked = book(service, "MORNING");

        AppointmentException invalid = assertThrows(AppointmentException.class,
                () -> appointments.arrive(booked.id(), booked.version(), RECEPTION));

        assertEquals("INVALID_TRANSITION", invalid.getType());
        assertEquals(409, invalid.getStatus());
    }

    @Test
    void staleVersionIsRejected() {
        AppointmentSnapshot booked = book(service, "MORNING");
        confirm(booked, LocalTime.of(9, 0), null);

        AppointmentException stale = assertThrows(AppointmentException.class,
                () -> appointments.reject(booked.id(), booked.version(), "Too late", RECEPTION));

        assertEquals("VERSION_MISMATCH", stale.getType());
        assertEquals(412, stale.getStatus());
    }

    @Test
    void inactiveDoctorCannotBeAssigned() {
        AppointmentSnapshot booked = book(service, "MORNING");
        UUID inactive = insertDoctor("Dr. Away", "INACTIVE");

        AppointmentException missing = assertThrows(AppointmentException.class,
                () -> appointments.confirm(booked.id(), booked.version(),
                        new ConfirmCommand(inactive, date, LocalTime.of(9, 0), null), RECEPTION));

        assertEquals(404, missing.getStatus());
    }

    @Test
    void inactiveServiceCannotBeBooked() {
        jdbc.update("update clinic.service set status = 'INACTIVE' where id = ?", service);
        entityManager.clear();

        AppointmentException missing = assertThrows(AppointmentException.class, () -> book(service, "MORNING"));

        assertEquals(404, missing.getStatus());
    }

    @Test
    void pastDateAndUnknownPeriodAreInvalid() {
        LocalDate yesterday = LocalDate.now(ClinicTime.ZONE).minusDays(1);

        AppointmentException past = assertThrows(AppointmentException.class, () -> appointments.request(
                new BookingCommand(service, yesterday, "MORNING", "Sok Dara", "012 345 678")));
        AppointmentException period = assertThrows(AppointmentException.class, () -> appointments.request(
                new BookingCommand(service, date, "EVENING", "Sok Dara", "012 345 678")));

        assertEquals("preferredDate", past.getErrors().getFirst().get("field"));
        assertEquals("preferredPeriod", period.getErrors().getFirst().get("field"));
    }

    @Test
    void samePhoneWrittenDifferentlyIsOnePatient() {
        appointments.request(new BookingCommand(service, date, "MORNING", "Sok Dara", "012 345 678"));
        appointments.request(new BookingCommand(service, date, "MORNING", "Dara Sok", "+855 12-345-678"));

        assertEquals(1, jdbc.queryForObject(
                "select count(*) from clinic.patient where phone = '+85512345678'", Integer.class));
        // Each appointment keeps the name as submitted.
        assertEquals(List.of("Dara Sok", "Sok Dara"), jdbc.queryForList(
                "select patient_name from clinic.appointment where patient_phone = '+85512345678' order by patient_name",
                String.class));
    }

    /** Last: the constraint violation marks the test transaction for rollback. */
    @Test
    void overlappingConfirmForTheSameDoctorIsRejected() {
        AppointmentSnapshot first = book(service, "MORNING");
        AppointmentSnapshot second = book(service, "MORNING");
        confirm(first, LocalTime.of(9, 0), null);

        // 9:30 starts inside the first appointment's 9:00-9:30 plus its 10-minute buffer.
        AppointmentException busy = assertThrows(AppointmentException.class,
                () -> confirm(second, LocalTime.of(9, 30), null));

        assertEquals("DOCTOR_UNAVAILABLE", busy.getType());
        assertEquals(409, busy.getStatus());
    }

    private AppointmentSnapshot book(UUID serviceId, String period) {
        phoneCounter++;
        return appointments.request(new BookingCommand(serviceId, date, period, "Patient " + phoneCounter,
                String.format("+8551%07d", phoneCounter)));
    }

    private AppointmentSnapshot confirm(AppointmentSnapshot booked, LocalTime start, Integer duration) {
        return appointments.confirm(booked.id(), booked.version(),
                new ConfirmCommand(doctor, date, start, duration), RECEPTION);
    }

    private UUID insertDoctor(String name, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into clinic.doctor (id, full_name, status) values (?, ?, ?)", id, name, status);
        return id;
    }
}
