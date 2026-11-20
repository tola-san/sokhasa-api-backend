package com.sokhasaclinic.clinicbackend.appointment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sokhasaclinic.clinicbackend.appointment.exception.AppointmentException;
import com.sokhasaclinic.clinicbackend.appointment.model.Actor;
import com.sokhasaclinic.clinicbackend.appointment.model.AppointmentStatus;
import com.sokhasaclinic.clinicbackend.appointment.service.AppointmentQueryService.ListFilter;
import com.sokhasaclinic.clinicbackend.cms.service.storage.MediaStorage;
import com.sokhasaclinic.clinicbackend.common.time.ClinicTime;
import java.time.LocalDate;
import java.time.LocalTime;
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

/** Preferred doctor revision (PD-01 to PD-10, approved November 20, 2026). Each test rolls back. */
@SpringBootTest(properties = "scheduling.enabled=false")
@EnabledIfEnvironmentVariable(named = "CLINIC_TEST_DATABASE_URL", matches = "jdbc:postgresql:.+")
@Transactional
class PreferredDoctorPostgresTests {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CLINIC_TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("CLINIC_TEST_DATABASE_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("CLINIC_TEST_DATABASE_PASSWORD"));
    }

    @MockitoBean MediaStorage mediaStorage;
    @Autowired AppointmentService appointments;
    @Autowired AppointmentQueryService queries;
    @Autowired JdbcTemplate jdbc;

    UUID service;
    UUID preferred;
    UUID other;
    String preferredSlug;
    LocalDate date;

    @BeforeEach
    void setUp() {
        service = UUID.randomUUID();
        date = LocalDate.now(ClinicTime.ZONE).plusDays(10);
        jdbc.update("""
                insert into clinic.service(id, slug, category_id, duration_minutes, buffer_minutes, status)
                select ?, ?, id, 30, 10, 'ACTIVE' from clinic.service_category order by sort_order limit 1
                """, service, "preferred-" + service);
        preferredSlug = "dr-pref-" + service.toString().substring(0, 8);
        preferred = publicDoctor("Dr. Preferred", preferredSlug);
        other = publicDoctor("Dr. Other", "dr-other-" + service.toString().substring(0, 8));
        link(preferred);
        link(other);
    }

    UUID publicDoctor(String name, String slug) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into clinic.doctor(id, full_name, slug) values (?, ?, ?)", id, name, slug);
        for (String language : new String[] {"km", "en", "zh"}) {
            jdbc.update("insert into clinic.doctor_translation(doctor_id, language_code, title) values (?, ?, 'Doctor')",
                    id, language);
        }
        return id;
    }

    void link(UUID doctor) {
        jdbc.update("insert into clinic.service_doctor(service_id, doctor_id) values (?, ?)", service, doctor);
    }

    void window(UUID doctor) {
        jdbc.update("""
                insert into clinic.doctor_booking_window(id, doctor_id, service_id, day, start_time, end_time)
                values (?, ?, ?, ?, time '14:00', time '17:00')
                """, UUID.randomUUID(), doctor, service, date);
    }

    BookingCommand period(String slug) {
        return new BookingCommand(service, date, "MORNING", "en", "Sok Dara", "012 345 678", null, null, null, slug);
    }

    BookingCommand exact(String slug) {
        return new BookingCommand(service, date, null, "en", "Sok Dara", "012 345 678", null, null,
                LocalTime.of(14, 0), slug);
    }

    UUID reservedDoctor(UUID appointment) {
        return jdbc.queryForObject("select reserved_doctor_id from clinic.appointment where id = ?", UUID.class, appointment);
    }

    @Test
    void noPreferenceIsStoredAsNull() {
        var booked = appointments.request(period(null));
        assertNull(booked.preferredDoctorId());
        var detail = queries.detail(booked.id()).data();
        assertNull(detail.preferredDoctorId());
        assertNull(detail.preferredDoctorName());
    }

    @Test
    void linkedPublicDoctorIsStoredAndShownToReception() {
        var booked = appointments.request(period(preferredSlug));
        assertEquals(AppointmentStatus.PENDING, booked.status());
        assertEquals(preferred, booked.preferredDoctorId());
        assertNull(booked.doctorId());
        var detail = queries.detail(booked.id()).data();
        assertEquals(preferred, detail.preferredDoctorId());
        assertEquals("Dr. Preferred", detail.preferredDoctorName());
        var item = queries.list(new ListFilter(null, date, null, null, booked.reference()), 0, 20).data().getFirst();
        assertEquals("Dr. Preferred", item.preferredDoctorName());
    }

    @Test
    void doctorNotLinkedToTheServiceIsInvalid() {
        String unlinked = "dr-unlinked-" + service.toString().substring(0, 8);
        publicDoctor("Dr. Unlinked", unlinked);
        var error = assertThrows(AppointmentException.class, () -> appointments.request(period(unlinked)));
        assertEquals("VALIDATION_ERROR", error.getType());
        assertEquals("preferredDoctorSlug", error.getErrors().getFirst().get("field"));
    }

    @Test
    void unknownOrNonPublicDoctorIsNotFound() {
        assertEquals("DOCTOR_NOT_FOUND", assertThrows(AppointmentException.class,
                () -> appointments.request(period("dr-nobody-here"))).getType());
        jdbc.update("update clinic.doctor set status = 'INACTIVE' where id = ?", preferred);
        assertEquals("DOCTOR_NOT_FOUND", assertThrows(AppointmentException.class,
                () -> appointments.request(period(preferredSlug))).getType());
        jdbc.update("update clinic.doctor set status = 'ACTIVE' where id = ?", preferred);
        jdbc.update("delete from clinic.doctor_translation where doctor_id = ? and language_code = 'zh'", preferred);
        assertEquals("DOCTOR_NOT_FOUND", assertThrows(AppointmentException.class,
                () -> appointments.request(period(preferredSlug))).getType());
    }

    @Test
    void receptionMayAssignAnotherDoctorAndThePreferenceIsKept() {
        var booked = appointments.request(period(preferredSlug));
        var confirmed = appointments.confirm(booked.id(), booked.version(),
                new ConfirmCommand(other, date, LocalTime.of(9, 0), null), Actor.staff("reception"));
        assertEquals(AppointmentStatus.CONFIRMED, confirmed.status());
        assertEquals(other, confirmed.doctorId());
        assertEquals(preferred, confirmed.preferredDoctorId());
    }

    @Test
    void preferenceIsKeptWhenThePreferredDoctorBecomesInactive() {
        var booked = appointments.request(period(preferredSlug));
        jdbc.update("update clinic.doctor set status = 'INACTIVE' where id = ?", preferred);
        assertEquals("Dr. Preferred", queries.detail(booked.id()).data().preferredDoctorName());
    }

    @Test
    void exactRequestReservesThePreferredDoctorWhenFree() {
        window(other);
        window(preferred);
        var booked = appointments.request(exact(preferredSlug));
        assertEquals(preferred, reservedDoctor(booked.id()));
    }

    @Test
    void exactRequestFallsBackWhenThePreferredDoctorIsBusy() {
        window(other);
        window(preferred);
        var first = appointments.request(exact(preferredSlug));
        assertEquals(preferred, reservedDoctor(first.id()));
        var second = appointments.request(exact(preferredSlug)); // The preferred doctor is now busy.
        assertEquals(other, reservedDoctor(second.id()));
        assertEquals(preferred, second.preferredDoctorId());
        // With both doctors busy the slot itself is gone, as before (not because of the preference).
        assertEquals("SLOT_UNAVAILABLE", assertThrows(AppointmentException.class,
                () -> appointments.request(exact(preferredSlug))).getType());
    }

    @Test
    void preferredDoctorWithoutAWindowDoesNotBlockTheRequest() {
        window(other);
        var booked = appointments.request(exact(preferredSlug));
        assertEquals(other, reservedDoctor(booked.id()));
        assertEquals(preferred, booked.preferredDoctorId());
    }

    @Test
    void sameKeyWithADifferentPreferenceConflicts() {
        UUID key = UUID.randomUUID();
        var first = appointments.request(period(preferredSlug), key);
        assertTrue(appointments.request(period(preferredSlug), key).replayed());
        assertEquals(first.appointment().id(), appointments.request(period(preferredSlug), key).appointment().id());
        assertEquals("IDEMPOTENCY_CONFLICT", assertThrows(AppointmentException.class,
                () -> appointments.request(period(null), key)).getType());
    }
}
