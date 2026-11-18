package com.sokhasaclinic.clinicbackend.appointment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentDtos.AppointmentListItem;
import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentDtos.BookingPeriodAvailability;
import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentDtos.DashboardSummary;
import com.sokhasaclinic.clinicbackend.appointment.exception.AppointmentException;
import com.sokhasaclinic.clinicbackend.appointment.model.Actor;
import com.sokhasaclinic.clinicbackend.appointment.model.AppointmentStatus;
import com.sokhasaclinic.clinicbackend.appointment.service.AppointmentQueryService.ListFilter;
import com.sokhasaclinic.clinicbackend.appointment.service.AppointmentService.BookingResult;
import com.sokhasaclinic.clinicbackend.cms.service.storage.MediaStorage;
import com.sokhasaclinic.clinicbackend.common.time.ClinicTime;
import java.time.LocalDate;
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
 * The appointment API decisions (AP-01 to AP-16, approved November 18, 2026) against a real
 * PostgreSQL database. Enabled by CLINIC_TEST_DATABASE_URL; each test rolls back.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@EnabledIfEnvironmentVariable(named = "CLINIC_TEST_DATABASE_URL", matches = "jdbc:postgresql:.+")
@Transactional
class AppointmentApiPostgresTests {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CLINIC_TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("CLINIC_TEST_DATABASE_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("CLINIC_TEST_DATABASE_PASSWORD"));
    }

    @MockitoBean
    MediaStorage mediaStorage;

    @Autowired
    AppointmentService appointments;

    @Autowired
    AppointmentQueryService queries;

    @Autowired
    JdbcTemplate jdbc;

    private UUID service;
    private UUID doctor;
    private LocalDate today;
    private LocalDate date;

    @BeforeEach
    void setUp() {
        service = jdbc.queryForObject(
                "select id from clinic.service where slug = 'chronic-disease-management'", UUID.class);
        jdbc.update("update clinic.service set duration_minutes = 30, buffer_minutes = 10 where id = ?", service);
        doctor = UUID.randomUUID();
        jdbc.update("insert into clinic.doctor (id, full_name, sort_order) values (?, 'Dr. Api Test', 99)", doctor);
        today = LocalDate.now(ClinicTime.ZONE);
        date = today.plusDays(20);
    }

    @Test
    void referencesCountUpPerPreferredDate() {
        String first = book("012 345 601", "Sok Dara").reference();
        String second = book("012 345 602", "Chan Lina").reference();

        String day = date.toString().replace("-", "");
        assertTrue(first.matches("SK-" + day + "-\\d{4}"), first);
        assertEquals(Integer.parseInt(first.substring(12)) + 1, Integer.parseInt(second.substring(12)));
    }

    @Test
    void repeatedKeyWithTheSameRequestReturnsTheFirstAppointment() {
        UUID key = UUID.randomUUID();
        BookingResult first = appointments.request(command("012 345 678", "Sok Dara"), key);
        // Typed differently, but the same request after normalising.
        BookingResult again = appointments.request(command("+855 12-345-678", "  Sok Dara "), key);

        assertFalse(first.replayed());
        assertTrue(again.replayed());
        assertEquals(first.appointment().id(), again.appointment().id());
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from clinic.appointment where patient_phone = '+85512345678'", Integer.class));
    }

    @Test
    void repeatedKeyWithADifferentRequestIsAConflict() {
        UUID key = UUID.randomUUID();
        appointments.request(command("012 345 678", "Sok Dara"), key);

        AppointmentException conflict = assertThrows(AppointmentException.class,
                () -> appointments.request(command("012 345 678", "Someone Else"), key));

        assertEquals("IDEMPOTENCY_CONFLICT", conflict.getType());
        assertEquals(409, conflict.getStatus());
    }

    @Test
    void bookingWindowIsSixtyDays() {
        appointments.request(new BookingCommand(service, today.plusDays(60), "MORNING", "Sok Dara", "012 345 678"));

        AppointmentException late = assertThrows(AppointmentException.class, () -> appointments.request(
                new BookingCommand(service, today.plusDays(61), "MORNING", "Sok Dara", "012 345 678")));

        assertEquals("preferredDate", late.getErrors().getFirst().get("field"));
    }

    @Test
    void patientFieldsAreKeptAndChecked() {
        AppointmentSnapshot booked = appointments.request(new BookingCommand(service, date, "MORNING", "zh",
                "Sok Dara", "012 345 678", "  sok@example.com ", "  First visit  "));

        assertEquals("zh", booked.languageCode());
        assertEquals("sok@example.com", booked.patientEmail());
        assertEquals("First visit", booked.notes());

        assertEquals("language", field(() -> appointments.request(
                new BookingCommand(service, date, "MORNING", "fr", "Sok Dara", "012 345 678", null, null))));
        assertEquals("email", field(() -> appointments.request(
                new BookingCommand(service, date, "MORNING", "en", "Sok Dara", "012 345 678", "not-an-email", null))));
        assertEquals("notes", field(() -> appointments.request(
                new BookingCommand(service, date, "MORNING", "en", "Sok Dara", "012 345 678", null, "x".repeat(501)))));
    }

    @Test
    void listFiltersSearchesAndKeepsOldestFirst() {
        AppointmentSnapshot first = book("012 345 611", "Sok Dara");
        AppointmentSnapshot second = book("097 777 888", "Chan Lina");
        appointments.confirm(second.id(), second.version(),
                new ConfirmCommand(doctor, date.plusDays(1), LocalTime.of(9, 0), null), Actor.staff("reception-1"));

        List<UUID> pending = ids(new ListFilter(AppointmentStatus.PENDING, date, null, null, null));
        assertTrue(pending.contains(first.id()));
        assertFalse(pending.contains(second.id()));

        // The confirmed one moved to the next day; a range finds both, oldest request first.
        List<UUID> range = ids(new ListFilter(null, null, date, date.plusDays(1), null));
        assertTrue(range.indexOf(first.id()) < range.indexOf(second.id()));
        assertEquals(List.of(second.id()), ids(new ListFilter(null, date.plusDays(1), null, null, "lina")));

        // Phone in local format, and reference.
        assertEquals(List.of(second.id()), ids(new ListFilter(null, null, null, null, "097 777 888")));
        assertEquals(List.of(first.id()), ids(new ListFilter(null, null, null, null, first.reference())));
    }

    @Test
    void listItemsCarryServiceNameDoctorAndClinicTimes() {
        AppointmentSnapshot booked = book("012 345 621", "Sok Dara");
        appointments.confirm(booked.id(), booked.version(),
                new ConfirmCommand(doctor, date, LocalTime.of(9, 30), null), Actor.staff("reception-1", "Reception One"));

        AppointmentListItem item = queries.list(new ListFilter(null, null, null, null, booked.reference()), 0, 20)
                .data().getFirst();

        assertEquals(jdbc.queryForObject(
                "select name from clinic.service_translation where service_id = ? and language_code = 'en'",
                String.class, service), item.serviceName());
        assertEquals("Dr. Api Test", item.doctorName());
        assertEquals("+07:00", item.startAt().getOffset().toString());
        assertEquals(LocalTime.of(9, 30), item.startAt().toLocalTime());
        assertEquals("Reception One", appointments.history(booked.id()).getLast().actorName());
        assertEquals(1, queries.detail(booked.id()).version());
    }

    @Test
    void rangeLongerThan62DaysIsInvalid() {
        assertEquals("to", field(() -> queries.list(new ListFilter(null, null, date, date.plusDays(62), null), 0, 20)));
        assertEquals("date", field(() -> queries.list(new ListFilter(null, date, date, date, null), 0, 20)));
        assertEquals("size", field(() -> queries.list(new ListFilter(null, null, null, null, null), 0, 101)));
    }

    @Test
    void summaryCountsPendingAndToday() {
        DashboardSummary before = queries.summary();
        AppointmentSnapshot booked = appointments.request(
                new BookingCommand(service, today, "AFTERNOON", "Sok Dara", "012 345 631"));
        appointments.confirm(booked.id(), booked.version(),
                new ConfirmCommand(doctor, today, LocalTime.of(16, 0), null), Actor.staff("reception-1"));
        book("012 345 632", "Chan Lina");

        DashboardSummary after = queries.summary();

        assertEquals(before.pending() + 1, after.pending());
        assertEquals(before.today().getOrDefault(AppointmentStatus.CONFIRMED, 0L) + 1,
                after.today().get(AppointmentStatus.CONFIRMED));
    }

    @Test
    void periodAvailabilityFollowsCapacityAndWindow() {
        jdbc.update("update clinic.booking_period set capacity_minutes = 40 where code = 'MORNING'");
        book("012 345 641", "Sok Dara");

        List<BookingPeriodAvailability> periods = queries.periodAvailability(date, service);

        assertEquals("MORNING", periods.getFirst().code());
        assertEquals("08:00", periods.getFirst().startTime());
        assertFalse(periods.getFirst().available());
        assertTrue(periods.get(1).available());
        assertTrue(queries.periodAvailability(today.plusDays(61), service).stream().noneMatch(BookingPeriodAvailability::available));
        assertEquals(404, assertThrows(AppointmentException.class,
                () -> queries.periodAvailability(date, UUID.randomUUID())).getStatus());
    }

    @Test
    void activeDoctorsOnly() {
        UUID away = UUID.randomUUID();
        jdbc.update("insert into clinic.doctor (id, full_name, status) values (?, 'Dr. Away', 'INACTIVE')", away);

        List<UUID> ids = queries.activeDoctors().stream().map(d -> d.id()).toList();

        assertTrue(ids.contains(doctor));
        assertFalse(ids.contains(away));
    }

    @Test
    void missingVersionIs428AfterTheExistenceCheck() {
        AppointmentSnapshot booked = book("012 345 651", "Sok Dara");

        assertEquals(404, assertThrows(AppointmentException.class,
                () -> appointments.arrive(UUID.randomUUID(), null, Actor.staff("reception-1"))).getStatus());
        assertEquals("VERSION_REQUIRED", assertThrows(AppointmentException.class,
                () -> appointments.reject(booked.id(), null, "", Actor.staff("reception-1"))).getType());
        // With the version, the empty reason is reported.
        assertEquals("reason", field(() -> appointments.reject(booked.id(), booked.version(), " ", Actor.staff("reception-1"))));
    }

    private AppointmentSnapshot book(String phone, String name) {
        return appointments.request(command(phone, name));
    }

    private BookingCommand command(String phone, String name) {
        return new BookingCommand(service, date, "MORNING", name, phone);
    }

    private List<UUID> ids(ListFilter filter) {
        return queries.list(filter, 0, 100).data().stream().map(AppointmentListItem::id).toList();
    }

    private static String field(Runnable call) {
        AppointmentException error = assertThrows(AppointmentException.class, call::run);
        assertEquals(400, error.getStatus());
        Object field = error.getErrors().isEmpty() ? null : error.getErrors().getFirst().get("field");
        assertNull(field == null ? "missing field" : null);
        return (String) field;
    }
}
