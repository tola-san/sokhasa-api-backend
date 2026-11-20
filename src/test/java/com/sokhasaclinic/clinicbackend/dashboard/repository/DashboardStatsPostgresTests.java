package com.sokhasaclinic.clinicbackend.dashboard.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sokhasaclinic.clinicbackend.appointment.service.AppointmentService;
import com.sokhasaclinic.clinicbackend.appointment.service.BookingCommand;
import com.sokhasaclinic.clinicbackend.cms.service.storage.MediaStorage;
import com.sokhasaclinic.clinicbackend.common.time.ClinicTime;
import com.sokhasaclinic.clinicbackend.dashboard.repository.DashboardStatsRepository.DoctorLoad;
import com.sokhasaclinic.clinicbackend.dashboard.repository.DashboardStatsRepository.ServiceCount;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
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

/**
 * SQL of the dashboard statistics revision (DS-02 to DS-09) on PostgreSQL. Data is moved to 2001 so
 * other rows in a shared test database never fall in the queried ranges. Each test rolls back.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@EnabledIfEnvironmentVariable(named = "CLINIC_TEST_DATABASE_URL", matches = "jdbc:postgresql:.+")
@Transactional
class DashboardStatsPostgresTests {

    private static final LocalDate DAY = LocalDate.of(2001, 3, 15);

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CLINIC_TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("CLINIC_TEST_DATABASE_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("CLINIC_TEST_DATABASE_PASSWORD"));
    }

    @MockitoBean MediaStorage mediaStorage;
    @Autowired AppointmentService appointments;
    @Autowired DashboardStatsRepository stats;
    @Autowired JdbcTemplate jdbc;

    UUID labService;
    UUID scanService;

    @BeforeEach
    void setUp() {
        labService = service("Dashboard lab");
        scanService = service("Dashboard scan");
    }

    UUID service(String englishName) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into clinic.service(id, slug, category_id, duration_minutes, buffer_minutes, status)
                select ?, ?, id, 30, 10, 'ACTIVE' from clinic.service_category order by sort_order limit 1
                """, id, "dashboard-" + id);
        jdbc.update("""
                insert into clinic.service_translation(service_id, language_code, name, short_description)
                values (?, 'en', ?, 'Dashboard test service')
                """, id, englishName);
        return id;
    }

    /** A real booking, then moved to {@code createdAt} (clinic-local time). */
    UUID requestAt(UUID service, LocalDateTime createdAt) {
        LocalDate preferred = LocalDate.now(ClinicTime.ZONE).plusDays(10);
        UUID id = appointments.request(new BookingCommand(service, preferred, "MORNING", "en", "Sok Dara",
                "012 345 678", null, null, null, null)).id();
        jdbc.update("update clinic.appointment set created_at = ? where id = ?", clinic(createdAt), id);
        jdbc.update("update clinic.appointment_status_history set changed_at = ? where appointment_id = ?",
                clinic(createdAt), id);
        return id;
    }

    void historyAt(UUID appointment, String fromStatus, String toStatus, LocalDateTime changedAt) {
        jdbc.update("""
                insert into clinic.appointment_status_history(appointment_id, from_status, to_status, actor_type,
                    actor_id, changed_at)
                values (?, ?, ?, 'STAFF', 'reception-1', ?)
                """, appointment, fromStatus, toStatus, clinic(changedAt));
    }

    UUID doctor(String name, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into clinic.doctor(id, full_name, status) values (?, ?, ?)", id, name, status);
        return id;
    }

    void window(UUID doctor, String start, String end) {
        jdbc.update("""
                insert into clinic.doctor_booking_window(id, doctor_id, service_id, day, start_time, end_time)
                values (?, ?, ?, ?, cast(? as time), cast(? as time))
                """, UUID.randomUUID(), doctor, labService, DAY, start, end);
    }

    /** Moves a booking onto {@link #DAY} with {@code doctor} at {@code hour}:00, in {@code status}. */
    void visit(UUID appointment, UUID doctor, int hour, String status) {
        jdbc.update("""
                update clinic.appointment set status = ?, doctor_id = ?, start_at = ?, end_at = ?,
                    confirmed_date = ?, confirmed_period = 'MORNING'
                where id = ?
                """, status, doctor, clinic(DAY.atTime(hour, 0)), clinic(DAY.atTime(hour, 30)), DAY, appointment);
    }

    static OffsetDateTime clinic(LocalDateTime local) {
        return local.atZone(ClinicTime.ZONE).toOffsetDateTime();
    }

    static Instant start(LocalDate day) {
        return day.atStartOfDay(ClinicTime.ZONE).toInstant();
    }

    @Test
    void requestsCountOnTheirClinicDay() {
        // 23:50 on Mar 14 clinic time is 16:50 UTC on Mar 14; 00:10 on Mar 15 is 17:10 UTC on Mar 14.
        requestAt(labService, DAY.minusDays(1).atTime(23, 50));
        requestAt(labService, DAY.atTime(0, 10));
        requestAt(scanService, DAY.atTime(18, 0));

        Map<LocalDate, Long> byDay = stats.requestedByBucket("day", start(DAY.minusDays(1)), start(DAY.plusDays(1)));

        assertEquals(Map.of(DAY.minusDays(1), 1L, DAY, 2L), byDay);
    }

    @Test
    void monthsAndYearsBucketByTheirFirstDay() {
        requestAt(labService, LocalDateTime.of(2001, 1, 31, 23, 0));
        requestAt(labService, LocalDateTime.of(2001, 2, 1, 1, 0));
        requestAt(labService, LocalDateTime.of(2001, 2, 20, 9, 0));
        Instant from = start(LocalDate.of(2001, 1, 1));
        Instant to = start(LocalDate.of(2002, 1, 1));

        assertEquals(Map.of(LocalDate.of(2001, 1, 1), 1L, LocalDate.of(2001, 2, 1), 2L),
                stats.requestedByBucket("month", from, to));
        assertEquals(Map.of(LocalDate.of(2001, 1, 1), 3L), stats.requestedByBucket("year", from, to));
    }

    @Test
    void confirmationsCountWhenTheyHappenNotWhenRequested() {
        UUID confirmed = requestAt(labService, DAY.minusDays(1).atTime(10, 0));
        historyAt(confirmed, "PENDING", "CONFIRMED", DAY.atTime(9, 0));
        UUID rejected = requestAt(labService, DAY.atTime(8, 0));
        historyAt(rejected, "PENDING", "REJECTED", DAY.atTime(9, 30));
        Instant from = start(DAY.minusDays(1));
        Instant to = start(DAY.plusDays(1));

        assertEquals(Map.of(DAY.minusDays(1), 1L, DAY, 1L), stats.requestedByBucket("day", from, to));
        assertEquals(Map.of(DAY, 1L), stats.changesByBucket("CONFIRMED", "day", from, to));
        assertTrue(stats.changesByBucket("COMPLETED", "day", from, to).isEmpty());
    }

    @Test
    void servicesAreOrderedByRequestsWithEnglishNames() {
        requestAt(scanService, DAY.atTime(9, 0));
        requestAt(labService, DAY.atTime(9, 0));
        requestAt(labService, DAY.atTime(10, 0));

        List<ServiceCount> counts = stats.requestsByService(start(DAY), start(DAY.plusDays(1)));

        assertEquals(List.of(new ServiceCount(labService, "Dashboard lab", 2),
                new ServiceCount(scanService, "Dashboard scan", 1)), counts);
    }

    @Test
    void windowsAndLoadAreReadForTheDay() {
        UUID busy = doctor("Dr. Dashboard Busy", "ACTIVE");
        window(busy, "08:00", "12:00");
        window(busy, "13:30", "17:00");
        UUID inVisit = requestAt(labService, DAY.minusDays(3).atTime(9, 0));
        visit(inVisit, busy, 8, "IN_PROGRESS");
        UUID done = requestAt(labService, DAY.minusDays(3).atTime(9, 5));
        visit(done, busy, 10, "COMPLETED");
        requestAt(labService, DAY.minusDays(3).atTime(9, 10)); // still PENDING: not counted

        assertEquals(2, stats.windowsOn(DAY).stream().filter(w -> w.doctorId().equals(busy)).count());
        DoctorLoad load = stats.loadOn(DAY).get(busy);
        assertEquals(2, load.patients());
        assertTrue(load.inVisit());
        assertNull(stats.loadOn(DAY.plusDays(1)).get(busy));
    }

    @Test
    void aDoctorWithoutAnActiveVisitIsNotInVisit() {
        UUID doctor = doctor("Dr. Dashboard Waiting", "ACTIVE");
        UUID confirmed = requestAt(labService, DAY.minusDays(3).atTime(9, 0));
        visit(confirmed, doctor, 11, "CONFIRMED");

        DoctorLoad load = stats.loadOn(DAY).get(doctor);
        assertEquals(1, load.patients());
        assertFalse(load.inVisit());
    }
}
