package com.sokhasaclinic.clinicbackend.appointment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sokhasaclinic.clinicbackend.appointment.exception.AppointmentException;
import com.sokhasaclinic.clinicbackend.appointment.model.Actor;
import com.sokhasaclinic.clinicbackend.appointment.repository.AppointmentRepository;
import com.sokhasaclinic.clinicbackend.cms.service.storage.MediaStorage;
import com.sokhasaclinic.clinicbackend.common.time.ClinicTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Concurrent booking and confirming against a real PostgreSQL database (acceptance cases "two
 * requests arrive at once for the last 40 minutes" and "confirm two overlapping appointments for one
 * doctor at the same moment"). Each call commits, so the test uses its own service and doctor and
 * deletes everything it created afterwards. Enabled by CLINIC_TEST_DATABASE_URL.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@EnabledIfEnvironmentVariable(named = "CLINIC_TEST_DATABASE_URL", matches = "jdbc:postgresql:.+")
class AppointmentConcurrencyPostgresTests {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CLINIC_TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("CLINIC_TEST_DATABASE_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("CLINIC_TEST_DATABASE_PASSWORD"));
    }

    private static final int ROUNDS = 5;

    @MockitoBean
    MediaStorage mediaStorage;

    @Autowired
    AppointmentService appointments;

    @Autowired
    AppointmentRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    private UUID service;
    private UUID doctor;
    private String phonePrefix;
    private int phoneCounter;

    @BeforeEach
    void setUp() {
        service = UUID.randomUUID();
        jdbc.update("""
                insert into clinic.service (id, slug, category_id, duration_minutes, buffer_minutes, status)
                select ?, ?, id, 40, 0, 'ACTIVE' from clinic.service_category order by sort_order limit 1
                """, service, "concurrency-test-" + service);
        doctor = UUID.randomUUID();
        jdbc.update("insert into clinic.doctor (id, full_name) values (?, 'Dr. Concurrency')", doctor);
        // A random 5-digit prefix keeps these patients apart from any other test data.
        phonePrefix = "+8559" + (10000 + (int) (Math.random() * 89999));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from clinic.appointment where service_id = ?", service);
        jdbc.update("delete from clinic.patient where phone like ?", phonePrefix + "%");
        jdbc.update("delete from clinic.doctor_booking_window where service_id = ?", service);
        jdbc.update("delete from clinic.doctor where id = ?", doctor);
        jdbc.update("delete from clinic.service where id = ?", service);
    }

    @Test
    void twoRequestsForTheLast40MinutesCannotBothSucceed() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            LocalDate date = LocalDate.now(ClinicTime.ZONE).plusDays(40 + round);
            for (int i = 0; i < 11; i++) {
                book(date);
            }
            assertEquals(440, repository.minutesUsed(date, "MORNING"));

            List<Outcome> outcomes = race(() -> book(date), () -> book(date));

            assertEquals(1, count(outcomes, null), "round " + round + ": " + outcomes);
            assertEquals(1, count(outcomes, "PERIOD_FULL"), "round " + round + ": " + outcomes);
            assertEquals(480, repository.minutesUsed(date, "MORNING"));
        }
    }

    @Test
    void twoOverlappingConfirmsForOneDoctorCannotBothSucceed() throws Exception {
        Actor first = Actor.staff("reception-1");
        Actor second = Actor.staff("reception-2");
        for (int round = 0; round < ROUNDS; round++) {
            LocalDate date = LocalDate.now(ClinicTime.ZONE).plusDays(50 + round);
            AppointmentSnapshot a = book(date);
            AppointmentSnapshot b = book(date);

            List<Outcome> outcomes = race(
                    () -> appointments.confirm(a.id(), a.version(),
                            new ConfirmCommand(doctor, date, LocalTime.of(9, 0), null), first),
                    () -> appointments.confirm(b.id(), b.version(),
                            new ConfirmCommand(doctor, date, LocalTime.of(9, 20), null), second));

            assertEquals(1, count(outcomes, null), "round " + round + ": " + outcomes);
            assertEquals(1, count(outcomes, "DOCTOR_UNAVAILABLE"), "round " + round + ": " + outcomes);
        }
    }

    @Test
    void twoExactRequestsForOneDoctorIntervalCannotBothSucceed() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            LocalDate date = LocalDate.now(ClinicTime.ZONE).plusDays(30 + round);
            jdbc.update("""
                    insert into clinic.doctor_booking_window(id, doctor_id, service_id, day, start_time, end_time)
                    values (?, ?, ?, ?, '14:00', '17:00')
                    """, UUID.randomUUID(), doctor, service, date);
            BookingCommand left = new BookingCommand(service, date, null, "en", "Patient A",
                    phonePrefix + "801", null, null, LocalTime.of(14, 0));
            BookingCommand right = new BookingCommand(service, date, null, "en", "Patient B",
                    phonePrefix + "802", null, null, LocalTime.of(14, 30));
            // Starts differ, but the 40-minute service intervals overlap.
            List<Outcome> outcomes = race(() -> appointments.request(left), () -> appointments.request(right));
            assertEquals(1, count(outcomes, null), outcomes.toString());
            assertEquals(1, count(outcomes, "SLOT_UNAVAILABLE"), outcomes.toString());
        }
    }

    @Test
    void concurrentExactRetriesWithOneKeyCreateOneReservation() throws Exception {
        LocalDate date = LocalDate.now(ClinicTime.ZONE).plusDays(35);
        jdbc.update("""
                insert into clinic.doctor_booking_window(id, doctor_id, service_id, day, start_time, end_time)
                values (?, ?, ?, ?, '14:00', '17:00')
                """, UUID.randomUUID(), doctor, service, date);
        UUID key = UUID.randomUUID();
        BookingCommand command = new BookingCommand(service, date, null, "en", "Patient Retry",
                phonePrefix + "803", null, null, LocalTime.of(14, 0));
        List<Outcome> outcomes = race(() -> appointments.request(command, key), () -> appointments.request(command, key));
        assertEquals(2, count(outcomes, null), outcomes.toString());
        assertEquals(1, jdbc.queryForObject("select count(*) from clinic.appointment where service_id = ?", Integer.class, service));
    }

    private AppointmentSnapshot book(LocalDate date) {
        phoneCounter++;
        return appointments.request(new BookingCommand(service, date, "MORNING", "Patient " + phoneCounter,
                phonePrefix + String.format("%03d", phoneCounter)));
    }

    /** Runs both calls as close together as possible; each result is null (success) or an error code. */
    private static List<Outcome> race(Callable<?> left, Callable<?> right) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Outcome>> futures = new ArrayList<>();
            for (Callable<?> call : List.of(left, right)) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        call.call();
                        return new Outcome(null);
                    } catch (AppointmentException e) {
                        return new Outcome(e.getType());
                    }
                }));
            }
            ready.await(10, TimeUnit.SECONDS);
            go.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private static long count(List<Outcome> outcomes, String error) {
        return outcomes.stream().filter(o -> java.util.Objects.equals(o.error(), error)).count();
    }

    private record Outcome(String error) {
    }
}
