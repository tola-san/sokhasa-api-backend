package com.sokhasaclinic.clinicbackend.doctor;

import static org.junit.jupiter.api.Assertions.*;

import com.sokhasaclinic.clinicbackend.appointment.exception.AppointmentException;
import com.sokhasaclinic.clinicbackend.appointment.model.Actor;
import com.sokhasaclinic.clinicbackend.appointment.service.*;
import com.sokhasaclinic.clinicbackend.cms.service.storage.MediaStorage;
import com.sokhasaclinic.clinicbackend.common.time.ClinicTime;
import com.sokhasaclinic.clinicbackend.doctor.dto.*;
import com.sokhasaclinic.clinicbackend.doctor.model.DoctorStatus;
import com.sokhasaclinic.clinicbackend.doctor.service.DoctorService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "scheduling.enabled=false")
@EnabledIfEnvironmentVariable(named = "CLINIC_TEST_DATABASE_URL", matches = "jdbc:postgresql:.+")
@Transactional
class DoctorManagementPostgresTests {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CLINIC_TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("CLINIC_TEST_DATABASE_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("CLINIC_TEST_DATABASE_PASSWORD"));
    }

    @MockitoBean MediaStorage storage;
    @Autowired DoctorService doctors;
    @Autowired BookingSlotService slots;
    @Autowired AppointmentService appointments;
    @Autowired AppointmentQueryService queries;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entityManager;
    UUID service;
    DoctorResponse doctor;
    LocalDate day;

    @BeforeEach
    void setUp() {
        service = UUID.randomUUID();
        jdbc.update("""
                insert into clinic.service(id, slug, category_id, duration_minutes, buffer_minutes, status)
                select ?, ?, id, 30, 10, 'ACTIVE' from clinic.service_category order by sort_order limit 1
                """, service, "doctor-management-" + service);
        doctor = doctors.create(new DoctorRequest("  Dr. Management  ", DoctorStatus.ACTIVE, 10));
        day = LocalDate.now(ClinicTime.ZONE).plusDays(10);
    }

    BookingWindowDtos.Request window(String start, String end) {
        return new BookingWindowDtos.Request(service, day, start, end);
    }

    AppointmentSnapshot book(String start) {
        return appointments.request(new BookingCommand(service, day, null, "en", "Patient", "012 345 678", null, null, LocalTime.parse(start)));
    }

    String code(Runnable action) { return assertThrows(AppointmentException.class, action::run).getType(); }

    @Test
    void createUpdateAndDeactivateKeepTheReceptionListCompatible() {
        assertEquals("Dr. Management", doctor.fullName());
        assertEquals(0, doctor.version());
        assertTrue(doctors.list().stream().anyMatch(d -> d.id().equals(doctor.id())));
        assertTrue(queries.activeDoctors().stream().anyMatch(d -> d.id().equals(doctor.id())));
        DoctorResponse updated = doctors.update(doctor.id(), "\"0\"", new DoctorRequest("Dr. Updated", DoctorStatus.INACTIVE, 2));
        assertEquals(1, updated.version());
        assertFalse(queries.activeDoctors().stream().anyMatch(d -> d.id().equals(doctor.id())));
        assertEquals("Dr. Updated", doctors.detail(doctor.id()).fullName());
    }

    @Test
    void doctorUpdatesRequireTheCurrentStrongVersion() {
        var body = new DoctorRequest("Dr. Updated", DoctorStatus.ACTIVE, 2);
        assertEquals("VERSION_REQUIRED", code(() -> doctors.update(doctor.id(), null, body)));
        assertEquals("VERSION_MISMATCH", code(() -> doctors.update(doctor.id(), "W/\"0\"", body)));
        doctors.update(doctor.id(), "\"0\"", body);
        assertEquals("VERSION_MISMATCH", code(() -> doctors.update(doctor.id(), "\"0\"", body)));
    }

    @Test
    void creatingVerifiedWindowsMakesPublicSlotsAppearAndUnusedWindowsCanBeEditedDeleted() {
        assertTrue(slots.availability(day, service).slots().isEmpty());
        var created = doctors.createWindow(doctor.id(), window("14:00", "15:00"));
        assertEquals("14:00", slots.availability(day, service).slots().getFirst().startAt().toLocalTime().toString());
        var updated = doctors.updateWindow(doctor.id(), created.id(), "\"0\"", window("15:00", "17:00"));
        assertEquals(1, updated.version());
        assertEquals("15:00", slots.availability(day, service).slots().getFirst().startAt().toLocalTime().toString());
        assertEquals(1, doctors.windows(doctor.id(), day, day, service).size());
        assertEquals(updated, doctors.window(doctor.id(), created.id()));
        doctors.deleteWindow(doctor.id(), created.id(), "\"1\"");
        assertTrue(slots.availability(day, service).slots().isEmpty());
    }

    @Test
    void overlapAndDateTimeValidationRejectInvalidWindowsButAllowAdjacentBreakWindows() {
        doctors.createWindow(doctor.id(), window("14:00", "15:00"));
        assertEquals("BOOKING_WINDOW_OVERLAP", code(() -> doctors.createWindow(doctor.id(), window("14:30", "16:00"))));
        doctors.createWindow(doctor.id(), window("15:00", "17:00"));
        assertEquals("VALIDATION_ERROR", code(() -> doctors.createWindow(doctor.id(), window("17:00", "16:00"))));
        assertEquals("VALIDATION_ERROR", code(() -> doctors.createWindow(doctor.id(), window("10:00", "10:30"))));
        assertEquals("VALIDATION_ERROR", code(() -> doctors.createWindow(doctor.id(), window("8:00", "12:00"))));
        assertEquals("VALIDATION_ERROR", code(() -> doctors.createWindow(doctor.id(),
                new BookingWindowDtos.Request(service, day.plusDays(61), "08:00", "12:00"))));
        assertEquals("VALIDATION_ERROR", code(() -> doctors.windows(doctor.id(), day, day.plusDays(62), null)));
    }

    @Test
    void missingInactiveAndCrossDoctorResourcesAreHandled() {
        assertEquals("DOCTOR_NOT_FOUND", code(() -> doctors.detail(UUID.randomUUID())));
        var created = doctors.createWindow(doctor.id(), window("14:00", "17:00"));
        var other = doctors.create(new DoctorRequest("Dr. Other", DoctorStatus.ACTIVE, 0));
        assertEquals("BOOKING_WINDOW_NOT_FOUND", code(() -> doctors.window(other.id(), created.id())));
        assertEquals("BOOKING_WINDOW_NOT_FOUND", code(() -> doctors.deleteWindow(other.id(), created.id(), "\"0\"")));
        doctors.update(doctor.id(), "\"0\"", new DoctorRequest(doctor.fullName(), DoctorStatus.INACTIVE, 0));
        assertEquals("DOCTOR_NOT_FOUND", code(() -> doctors.createWindow(doctor.id(), window("08:00", "12:00"))));
        jdbc.update("update clinic.service set status = 'INACTIVE' where id = ?", service);
        entityManager.clear();
        assertEquals("SERVICE_NOT_FOUND", code(() -> doctors.createWindow(other.id(), window("08:00", "12:00"))));
    }

    @Test
    void windowChangesRequireTheCurrentVersion() {
        var created = doctors.createWindow(doctor.id(), window("14:00", "17:00"));
        assertEquals("VERSION_REQUIRED", code(() -> doctors.deleteWindow(doctor.id(), created.id(), null)));
        assertEquals("VERSION_MISMATCH", code(() -> doctors.updateWindow(doctor.id(), created.id(), "\"9\"", window("08:00", "12:00"))));
    }

    @Test
    void bookedWindowsRemainImmutableEvenAfterRejectionButDoctorCanThenDeactivate() {
        var created = doctors.createWindow(doctor.id(), window("14:00", "17:00"));
        var booked = book("14:00");
        assertEquals("SCHEDULE_IN_USE", code(() -> doctors.updateWindow(doctor.id(), created.id(), "\"0\"", window("08:00", "12:00"))));
        assertEquals("SCHEDULE_IN_USE", code(() -> doctors.deleteWindow(doctor.id(), created.id(), "\"0\"")));
        assertEquals("DOCTOR_IN_USE", code(() -> doctors.update(doctor.id(), "\"0\"", new DoctorRequest(doctor.fullName(), DoctorStatus.INACTIVE, 0))));
        appointments.reject(booked.id(), booked.version(), "Cannot attend", Actor.staff("reception"));
        assertEquals("SCHEDULE_IN_USE", code(() -> doctors.deleteWindow(doctor.id(), created.id(), "\"0\"")));
        assertEquals(DoctorStatus.INACTIVE, doctors.update(doctor.id(), "\"0\"", new DoctorRequest(doctor.fullName(), DoctorStatus.INACTIVE, 0)).status());
    }

    @Test
    void changedConfirmationProtectsItsDestinationWindowAndDoctor() {
        doctors.createWindow(doctor.id(), window("14:00", "15:00"));
        var target = doctors.createWindow(doctor.id(), window("15:00", "17:00"));
        var booked = book("14:00");
        appointments.confirm(booked.id(), booked.version(), new ConfirmCommand(doctor.id(), day, LocalTime.of(15, 30), null), Actor.staff("reception"));
        assertEquals("SCHEDULE_IN_USE", code(() -> doctors.deleteWindow(doctor.id(), target.id(), "\"0\"")));
        assertEquals("DOCTOR_IN_USE", code(() -> doctors.update(doctor.id(), "\"0\"", new DoctorRequest(doctor.fullName(), DoctorStatus.INACTIVE, 0))));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("delete from clinic.doctor_booking_window where id = ?", target.id()));
    }
}
