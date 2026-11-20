package com.sokhasaclinic.clinicbackend.dashboard.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sokhasaclinic.clinicbackend.common.time.ClinicTime;
import com.sokhasaclinic.clinicbackend.dashboard.dto.DashboardDtos.DashboardStats;
import com.sokhasaclinic.clinicbackend.dashboard.dto.DashboardDtos.DoctorToday;
import com.sokhasaclinic.clinicbackend.dashboard.dto.DashboardDtos.TimeWindow;
import com.sokhasaclinic.clinicbackend.dashboard.dto.DashboardDtos.TopService;
import com.sokhasaclinic.clinicbackend.dashboard.repository.DashboardStatsRepository;
import com.sokhasaclinic.clinicbackend.dashboard.repository.DashboardStatsRepository.DoctorLoad;
import com.sokhasaclinic.clinicbackend.dashboard.repository.DashboardStatsRepository.DoctorWindow;
import com.sokhasaclinic.clinicbackend.dashboard.repository.DashboardStatsRepository.ServiceCount;
import com.sokhasaclinic.clinicbackend.doctor.model.Doctor;
import com.sokhasaclinic.clinicbackend.doctor.model.DoctorStatus;
import com.sokhasaclinic.clinicbackend.doctor.repository.DoctorRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Dashboard statistics revision (DS-01 to DS-10), without a database. */
class DashboardStatsServiceTests {

    // 2026-11-20 00:30 in Phnom Penh is 2026-11-19 17:30 UTC: "today" must be the clinic date.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-19T17:30:00Z"), ClinicTime.ZONE);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);

    private DashboardStatsRepository stats;
    private DoctorRepository doctors;
    private DashboardStatsService service;

    @BeforeEach
    void setUp() {
        stats = mock(DashboardStatsRepository.class);
        doctors = mock(DoctorRepository.class);
        service = new DashboardStatsService(stats, doctors, CLOCK);
    }

    @Test
    void weekIsTheLastSevenClinicDays() {
        List<LocalDate> starts = StatsRange.WEEK.bucketStarts(TODAY);
        assertEquals(7, starts.size());
        assertEquals(LocalDate.of(2026, 11, 14), starts.getFirst());
        assertEquals(TODAY, starts.getLast());
    }

    @Test
    void monthIsTheLastTwelveCalendarMonths() {
        List<LocalDate> starts = StatsRange.MONTH.bucketStarts(TODAY);
        assertEquals(12, starts.size());
        assertEquals(LocalDate.of(2025, 12, 1), starts.getFirst());
        assertEquals(LocalDate.of(2026, 11, 1), starts.getLast());
    }

    @Test
    void yearIsTheLastFiveCalendarYears() {
        List<LocalDate> starts = StatsRange.YEAR.bucketStarts(TODAY);
        assertEquals(List.of(LocalDate.of(2022, 1, 1), LocalDate.of(2023, 1, 1), LocalDate.of(2024, 1, 1),
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1)), starts);
    }

    @Test
    void statsFillEmptyBucketsAndQueryClinicDayBoundaries() {
        Instant from = Instant.parse("2026-11-13T17:00:00Z"); // 2026-11-14 00:00 clinic time
        Instant to = Instant.parse("2026-11-20T17:00:00Z");   // 2026-11-21 00:00 clinic time
        when(stats.requestedByBucket("day", from, to)).thenReturn(Map.of(TODAY, 5L, LocalDate.of(2026, 11, 14), 2L));
        when(stats.changesByBucket("CONFIRMED", "day", from, to)).thenReturn(Map.of(TODAY, 3L));
        when(stats.changesByBucket("COMPLETED", "day", from, to)).thenReturn(Map.of());
        when(stats.requestsByService(from, to)).thenReturn(List.of(new ServiceCount(UUID.randomUUID(), "Lab", 7)));

        DashboardStats result = service.stats(StatsRange.WEEK);

        assertEquals(LocalDate.of(2026, 11, 14), result.from());
        assertEquals(TODAY, result.to());
        assertEquals(7, result.series().size());
        assertEquals(2, result.series().getFirst().requested());
        assertEquals(0, result.series().get(1).requested());
        assertEquals(5, result.series().getLast().requested());
        assertEquals(3, result.series().getLast().confirmed());
        assertEquals(0, result.series().getLast().completed());
        assertEquals(7, result.totalRequested());
        verify(stats).requestsByService(eq(from), eq(to));
    }

    @Test
    void topServicesListThreeThenOthers() {
        List<ServiceCount> counts = List.of(
                new ServiceCount(UUID.randomUUID(), "General checkup", 24),
                new ServiceCount(UUID.randomUUID(), "Laboratory", 14),
                new ServiceCount(UUID.randomUUID(), "Preventive screening", 12),
                new ServiceCount(UUID.randomUUID(), "Imaging", 8),
                new ServiceCount(UUID.randomUUID(), "Vaccination", 3));

        List<TopService> top = DashboardStatsService.topServices(counts, 61);

        assertEquals(4, top.size());
        assertEquals("General checkup", top.getFirst().serviceName());
        assertEquals(new BigDecimal("39.3"), top.getFirst().share());
        assertEquals(new BigDecimal("23.0"), top.get(1).share());
        TopService others = top.getLast();
        assertNull(others.serviceId());
        assertEquals("Others", others.serviceName());
        assertEquals(11, others.count());
        assertEquals(new BigDecimal("18.0"), others.share());
    }

    @Test
    void noOthersWithThreeServicesOrFewerAndNothingWithoutRequests() {
        List<ServiceCount> three = List.of(
                new ServiceCount(UUID.randomUUID(), "A", 2),
                new ServiceCount(UUID.randomUUID(), "B", 1),
                new ServiceCount(UUID.randomUUID(), "C", 1));
        assertEquals(3, DashboardStatsService.topServices(three, 4).size());
        assertTrue(DashboardStatsService.topServices(List.of(), 0).isEmpty());
    }

    @Test
    void overlappingAndTouchingWindowsMerge() {
        UUID doctor = UUID.randomUUID();
        List<TimeWindow> merged = DashboardStatsService.merge(List.of(
                window(doctor, "13:30", "17:00"),
                window(doctor, "08:00", "10:00"),
                window(doctor, "10:00", "12:00"),
                window(doctor, "09:00", "11:00"),
                window(doctor, "14:00", "15:00")));
        assertEquals(List.of(new TimeWindow("08:00", "12:00"), new TimeWindow("13:30", "17:00")), merged);
    }

    @Test
    void doctorsTodayListsActiveDoctorsWithWindowsOrPatientsInDisplayOrder() {
        Doctor withWindow = doctor("Dr. A");
        Doctor withPatients = doctor("Dr. B");
        Doctor idle = doctor("Dr. C");
        when(doctors.findByStatusOrderBySortOrderAscFullNameAsc(DoctorStatus.ACTIVE))
                .thenReturn(List.of(withWindow, withPatients, idle));
        // Read the mocks' ids before stubbing the repository: Mockito forbids nested stubbing.
        UUID windowDoctor = withWindow.getId();
        UUID busyDoctor = withPatients.getId();
        when(stats.windowsOn(TODAY)).thenReturn(List.of(window(windowDoctor, "08:00", "12:00")));
        when(stats.loadOn(TODAY)).thenReturn(Map.of(busyDoctor, new DoctorLoad(3, true)));

        var result = service.doctorsToday();

        assertEquals(TODAY, result.date());
        assertEquals(List.of("Dr. A", "Dr. B"), result.doctors().stream().map(DoctorToday::fullName).toList());
        DoctorToday a = result.doctors().getFirst();
        assertEquals(List.of(new TimeWindow("08:00", "12:00")), a.windows());
        assertEquals(0, a.patients());
        assertFalse(a.inVisit());
        DoctorToday b = result.doctors().getLast();
        assertTrue(b.windows().isEmpty());
        assertEquals(3, b.patients());
        assertTrue(b.inVisit());
        verify(stats).loadOn(any(LocalDate.class));
    }

    private static DoctorWindow window(UUID doctor, String start, String end) {
        return new DoctorWindow(doctor, LocalTime.parse(start), LocalTime.parse(end));
    }

    private static Doctor doctor(String name) {
        Doctor doctor = mock(Doctor.class);
        UUID id = UUID.randomUUID();
        when(doctor.getId()).thenReturn(id);
        when(doctor.getFullName()).thenReturn(name);
        return doctor;
    }
}
