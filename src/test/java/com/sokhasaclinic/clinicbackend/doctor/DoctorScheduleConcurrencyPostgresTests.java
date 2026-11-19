package com.sokhasaclinic.clinicbackend.doctor;

import static org.junit.jupiter.api.Assertions.*;

import com.sokhasaclinic.clinicbackend.appointment.exception.AppointmentException;
import com.sokhasaclinic.clinicbackend.appointment.service.AppointmentService;
import com.sokhasaclinic.clinicbackend.appointment.service.BookingCommand;
import com.sokhasaclinic.clinicbackend.cms.service.storage.MediaStorage;
import com.sokhasaclinic.clinicbackend.common.time.ClinicTime;
import com.sokhasaclinic.clinicbackend.doctor.dto.*;
import com.sokhasaclinic.clinicbackend.doctor.model.DoctorStatus;
import com.sokhasaclinic.clinicbackend.doctor.service.DoctorService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "scheduling.enabled=false")
@EnabledIfEnvironmentVariable(named = "CLINIC_TEST_DATABASE_URL", matches = "jdbc:postgresql:.+")
class DoctorScheduleConcurrencyPostgresTests {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CLINIC_TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("CLINIC_TEST_DATABASE_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("CLINIC_TEST_DATABASE_PASSWORD"));
    }
    @MockitoBean MediaStorage storage;
    @Autowired DoctorService doctors;
    @Autowired AppointmentService appointments;
    @Autowired JdbcTemplate jdbc;
    DoctorResponse doctor;
    UUID service;
    LocalDate day;
    String phone;

    @BeforeEach void setup() {
        service = UUID.randomUUID();
        jdbc.update("""
                insert into clinic.service(id, slug, category_id, duration_minutes, buffer_minutes, status)
                select ?, ?, id, 30, 0, 'ACTIVE' from clinic.service_category order by sort_order limit 1
                """, service, "schedule-race-" + service);
        doctor = doctors.create(new DoctorRequest("Dr. Schedule Race", DoctorStatus.ACTIVE, 0));
        day = LocalDate.now(ClinicTime.ZONE).plusDays(25);
        phone = "+8559" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
    }
    @AfterEach void cleanup() {
        jdbc.update("delete from clinic.appointment where service_id = ?", service);
        jdbc.update("delete from clinic.doctor_booking_window where service_id = ?", service);
        jdbc.update("delete from clinic.doctor where id = ?", doctor.id());
        jdbc.update("delete from clinic.service where id = ?", service);
        jdbc.update("delete from clinic.patient where phone = ?", phone);
    }
    BookingWindowDtos.Request window() { return new BookingWindowDtos.Request(service, day, "14:00", "17:00"); }
    Object book() { return appointments.request(new BookingCommand(service, day, null, "en", "Patient", phone, null, null, LocalTime.of(14, 0))); }

    @Test void overlappingScheduleCreatesAreSerialized() throws Exception {
        var results = race(() -> doctors.createWindow(doctor.id(), window()), () -> doctors.createWindow(doctor.id(), window()));
        assertEquals(1, results.stream().filter("OK"::equals).count());
        assertEquals(1, results.stream().filter("BOOKING_WINDOW_OVERLAP"::equals).count());
    }

    @Test void aDoctorCannotBeDeactivatedWhileANewReservationCommits() throws Exception {
        doctors.createWindow(doctor.id(), window());
        var results = race(this::book, () -> doctors.update(doctor.id(), "\"0\"", new DoctorRequest(doctor.fullName(), DoctorStatus.INACTIVE, 0)));
        assertEquals(1, results.stream().filter("OK"::equals).count(), results.toString());
        assertTrue(results.contains("DOCTOR_IN_USE") || results.contains("SLOT_UNAVAILABLE"), results.toString());
        int reservations = jdbc.queryForObject("select count(*) from clinic.appointment where reserved_doctor_id = ?", Integer.class, doctor.id());
        assertFalse(reservations > 0 && doctors.detail(doctor.id()).status() == DoctorStatus.INACTIVE);
    }

    @Test void deletingAWindowCannotInvalidateAConcurrentReservation() throws Exception {
        var window = doctors.createWindow(doctor.id(), window());
        var results = race(this::book, () -> { doctors.deleteWindow(doctor.id(), window.id(), "\"0\""); return null; });
        assertEquals(1, results.stream().filter("OK"::equals).count(), results.toString());
        assertTrue(results.contains("SCHEDULE_IN_USE") || results.contains("SLOT_UNAVAILABLE"), results.toString());
    }

    private static List<String> race(Callable<?> first, Callable<?> second) throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var go = new CountDownLatch(1);
            var tasks = List.of(first, second).stream().map(call -> pool.submit(() -> {
                ready.countDown();
                if (!go.await(10, TimeUnit.SECONDS)) throw new AssertionError("Race did not start");
                try { call.call(); return "OK"; } catch (AppointmentException ex) { return ex.getType(); }
            })).toList();
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            return List.of(tasks.get(0).get(20, TimeUnit.SECONDS), tasks.get(1).get(20, TimeUnit.SECONDS));
        }
    }
}
