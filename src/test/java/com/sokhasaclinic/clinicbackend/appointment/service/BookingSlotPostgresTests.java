package com.sokhasaclinic.clinicbackend.appointment.service;

import static org.junit.jupiter.api.Assertions.*;

import com.sokhasaclinic.clinicbackend.appointment.exception.AppointmentException;
import com.sokhasaclinic.clinicbackend.appointment.model.Actor;
import com.sokhasaclinic.clinicbackend.appointment.model.AppointmentStatus;
import com.sokhasaclinic.clinicbackend.cms.service.storage.MediaStorage;
import com.sokhasaclinic.clinicbackend.common.time.ClinicTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
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

@SpringBootTest(properties = "scheduling.enabled=false")
@EnabledIfEnvironmentVariable(named = "CLINIC_TEST_DATABASE_URL", matches = "jdbc:postgresql:.+")
@Transactional
class BookingSlotPostgresTests {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CLINIC_TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("CLINIC_TEST_DATABASE_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("CLINIC_TEST_DATABASE_PASSWORD"));
    }

    @MockitoBean MediaStorage mediaStorage;
    @Autowired AppointmentService appointments;
    @Autowired AppointmentQueryService queries;
    @Autowired BookingSlotService slots;
    @Autowired JdbcTemplate jdbc;
    UUID service;
    UUID doctor;
    LocalDate date;

    @BeforeEach
    void setUp() {
        service = UUID.randomUUID();
        doctor = UUID.randomUUID();
        date = LocalDate.now(ClinicTime.ZONE).plusDays(10);
        jdbc.update("""
                insert into clinic.service(id, slug, category_id, duration_minutes, buffer_minutes, status)
                select ?, ?, id, 30, 10, 'ACTIVE' from clinic.service_category order by sort_order limit 1
                """, service, "slots-" + service);
        jdbc.update("insert into clinic.doctor(id, full_name) values (?, 'Dr. Slots')", doctor);
    }

    void window(UUID resource, String start, String end) {
        jdbc.update("""
                insert into clinic.doctor_booking_window(id, doctor_id, service_id, day, start_time, end_time)
                values (?, ?, ?, ?, cast(? as time), cast(? as time))
                """, UUID.randomUUID(), resource, service, date, start, end);
    }

    BookingCommand command(String time) {
        return new BookingCommand(service, date, null, "en", "Sok Dara", "012 345 678", null, null, LocalTime.parse(time));
    }

    List<String> times() {
        return slots.availability(date, service).slots().stream().map(s -> s.startAt().toLocalTime().toString()).toList();
    }

    @Test
    void missingSchedulesNeverFabricateAvailability() {
        assertTrue(times().isEmpty());
        assertEquals("SLOT_UNAVAILABLE", assertThrows(AppointmentException.class,
                () -> appointments.request(command("14:00"))).getType());
    }

    @Test
    void windowsRespectBreaksBuffersClosingAndClinicOffset() {
        window(doctor, "14:00", "15:00");
        window(doctor, "15:30", "17:00");
        assertEquals(List.of("14:00", "15:30", "16:00"), times());
        var response = slots.availability(date, service);
        assertEquals("Asia/Phnom_Penh", response.timeZone());
        assertEquals(ZoneOffset.ofHours(7), response.slots().getFirst().startAt().getOffset());
        assertEquals(LocalTime.of(14, 30), response.slots().getFirst().endAt().toLocalTime());
    }

    @Test
    void pendingReservesTimeAndRejectionReleasesIt() {
        window(doctor, "14:00", "17:00");
        var booked = appointments.request(command("14:00"));
        assertEquals(AppointmentStatus.PENDING, booked.status());
        assertNull(booked.doctorId());
        assertNull(booked.startAt());
        assertEquals(LocalTime.of(14, 0), booked.preferredStartTime());
        assertFalse(times().contains("14:00"));
        assertFalse(times().contains("14:30")); // 10-minute turnover still occupies the doctor.
        assertEquals("14:00", queries.detail(booked.id()).data().preferredStartTime());
        appointments.reject(booked.id(), booked.version(), "Patient cannot attend", Actor.staff("reception"));
        assertTrue(times().contains("14:00"));
    }

    @Test
    void anotherDoctorCanSupportTheSameStartAndResponsesAreDeduplicated() {
        UUID other = UUID.randomUUID();
        jdbc.update("insert into clinic.doctor(id, full_name) values (?, 'Dr. Other')", other);
        window(doctor, "14:00", "17:00");
        window(other, "14:00", "17:00");
        assertEquals(1, times().stream().filter("14:00"::equals).count());
        appointments.request(command("14:00"));
        assertTrue(times().contains("14:00"));
        appointments.request(command("14:00"));
        assertFalse(times().contains("14:00"));
    }

    @Test
    void confirmTransfersReservationAndKeepsPatientPreference() {
        window(doctor, "14:00", "17:00");
        var booked = appointments.request(command("14:00"));
        var confirmed = appointments.confirm(booked.id(), booked.version(),
                new ConfirmCommand(doctor, date, LocalTime.of(15, 0), null), Actor.staff("reception"));
        assertEquals(LocalTime.of(14, 0), confirmed.preferredStartTime());
        assertEquals(AppointmentStatus.CONFIRMED, confirmed.status());
        assertTrue(times().contains("14:00"));
        assertFalse(times().contains("15:00"));
        assertNull(jdbc.queryForObject("select reserved_doctor_id from clinic.appointment where id = ?", UUID.class, booked.id()));
    }

    @Test
    void idempotentRetryDoesNotReserveTwiceAndChangedTimeConflicts() {
        window(doctor, "14:00", "17:00");
        UUID key = UUID.randomUUID();
        var first = appointments.request(command("14:00"), key);
        var again = appointments.request(command("14:00"), key);
        assertEquals(first.appointment().id(), again.appointment().id());
        assertTrue(again.replayed());
        assertEquals("IDEMPOTENCY_CONFLICT", assertThrows(AppointmentException.class,
                () -> appointments.request(command("15:00"), key)).getType());
        jdbc.update("update clinic.service set duration_minutes = 480, status = 'INACTIVE' where id = ?", service);
        assertEquals(first.appointment().id(), appointments.request(command("14:00"), key).appointment().id());
    }

    @Test
    void legacyPendingBlocksExactStartsAcrossServicesSharingAPeriod() {
        window(doctor, "14:00", "17:00");
        appointments.request(new BookingCommand(service, date, "AFTERNOON", "Sok Dara", "012 345 678"));
        assertTrue(times().isEmpty());
        assertEquals("SLOT_UNAVAILABLE", assertThrows(AppointmentException.class,
                () -> appointments.request(command("14:00"))).getType());
    }

    @Test
    void invalidGridSelectorsAndWindowAreRejected() {
        assertEquals("VALIDATION_ERROR", assertThrows(AppointmentException.class,
                () -> appointments.request(command("14:15"))).getType());
        assertEquals("VALIDATION_ERROR", assertThrows(AppointmentException.class,
                () -> appointments.request(new BookingCommand(service, date, "AFTERNOON", "en", "Sok Dara",
                        "012 345 678", null, null, LocalTime.of(14, 0)))).getType());
        assertEquals("VALIDATION_ERROR", assertThrows(AppointmentException.class,
                () -> slots.availability(date.plusDays(61), service)).getType());
    }

    @Test
    void legacyConfirmationCannotOverlapAPendingExactReservation() {
        window(doctor, "14:00", "17:00");
        appointments.request(command("14:00"));
        var legacy = appointments.request(new BookingCommand(service, date, "MORNING", "Another patient", "012 345 679"));
        assertEquals("DOCTOR_UNAVAILABLE", assertThrows(AppointmentException.class,
                () -> appointments.confirm(legacy.id(), legacy.version(),
                        new ConfirmCommand(doctor, date, LocalTime.of(14, 30), null), Actor.staff("reception"))).getType());
    }

    @Test
    void confirmationWithoutAVerifiedWindowFailsWithoutDroppingReservation() {
        window(doctor, "14:00", "15:00");
        var booked = appointments.request(command("14:00"));
        assertEquals("SLOT_UNAVAILABLE", assertThrows(AppointmentException.class,
                () -> appointments.confirm(booked.id(), booked.version(),
                        new ConfirmCommand(doctor, date, LocalTime.of(16, 0), null), Actor.staff("reception"))).getType());
        assertFalse(times().contains("14:00"));
    }

    @Test
    void usedScheduleWindowsCannotBeEdited() {
        window(doctor, "14:00", "17:00");
        appointments.request(command("14:00"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("update clinic.doctor_booking_window set end_time = '15:00' where service_id = ?", service));
    }

    @Test
    void inactiveDoctorsAndPastStartsAreNotOffered() {
        window(doctor, "14:00", "17:00");
        jdbc.update("update clinic.doctor set status = 'INACTIVE' where id = ?", doctor);
        assertTrue(times().isEmpty());
        LocalDate today = LocalDate.now(ClinicTime.ZONE);
        var sameDay = slots.availability(today, service);
        assertTrue(sameDay.slots().stream().allMatch(s -> s.startAt().toInstant().isAfter(java.time.Instant.now())));
    }
}
