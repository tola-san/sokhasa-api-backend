package com.sokhasaclinic.clinicbackend.dashboard.service;

import com.sokhasaclinic.clinicbackend.common.time.ClinicTime;
import com.sokhasaclinic.clinicbackend.dashboard.dto.DashboardDtos.DashboardStats;
import com.sokhasaclinic.clinicbackend.dashboard.dto.DashboardDtos.DoctorToday;
import com.sokhasaclinic.clinicbackend.dashboard.dto.DashboardDtos.DoctorsToday;
import com.sokhasaclinic.clinicbackend.dashboard.dto.DashboardDtos.StatsBucket;
import com.sokhasaclinic.clinicbackend.dashboard.dto.DashboardDtos.TimeWindow;
import com.sokhasaclinic.clinicbackend.dashboard.dto.DashboardDtos.TopService;
import com.sokhasaclinic.clinicbackend.dashboard.repository.DashboardStatsRepository;
import com.sokhasaclinic.clinicbackend.dashboard.repository.DashboardStatsRepository.DoctorLoad;
import com.sokhasaclinic.clinicbackend.dashboard.repository.DashboardStatsRepository.DoctorWindow;
import com.sokhasaclinic.clinicbackend.dashboard.repository.DashboardStatsRepository.ServiceCount;
import com.sokhasaclinic.clinicbackend.doctor.model.DoctorStatus;
import com.sokhasaclinic.clinicbackend.doctor.repository.DoctorRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard statistics revision (DS-01 to DS-12, approved November 20, 2026). Counts come from SQL
 * on each request; nothing is stored. Days, months and years follow the clinic time zone (DS-02).
 */
@Service
@Transactional(readOnly = true)
public class DashboardStatsService {

    /** DS-06: services listed by name before the rest become "Others". */
    static final int TOP_SERVICES = 3;
    static final String OTHERS = "Others";
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final DashboardStatsRepository stats;
    private final DoctorRepository doctors;
    private final Clock clock;

    @Autowired
    public DashboardStatsService(DashboardStatsRepository stats, DoctorRepository doctors) {
        this(stats, doctors, Clock.systemUTC());
    }

    DashboardStatsService(DashboardStatsRepository stats, DoctorRepository doctors, Clock clock) {
        this.stats = stats;
        this.doctors = doctors;
        this.clock = clock;
    }

    /** DS-01 to DS-07: every bucket of the range, oldest first, and the top services. */
    public DashboardStats stats(StatsRange range) {
        LocalDate today = today();
        List<LocalDate> starts = range.bucketStarts(today);
        Instant from = startOf(starts.getFirst());
        Instant to = startOf(today.plusDays(1));

        Map<LocalDate, Long> requested = stats.requestedByBucket(range.sqlUnit(), from, to);
        Map<LocalDate, Long> confirmed = stats.changesByBucket("CONFIRMED", range.sqlUnit(), from, to);
        Map<LocalDate, Long> completed = stats.changesByBucket("COMPLETED", range.sqlUnit(), from, to);
        List<StatsBucket> series = starts.stream()
                .map(start -> new StatsBucket(start, requested.getOrDefault(start, 0L),
                        confirmed.getOrDefault(start, 0L), completed.getOrDefault(start, 0L)))
                .toList();

        List<ServiceCount> byService = stats.requestsByService(from, to);
        long total = byService.stream().mapToLong(ServiceCount::count).sum();
        return new DashboardStats(range, starts.getFirst(), today, series, total, topServices(byService, total));
    }

    /** DS-08 to DS-10: active doctors with a window or an appointment today, in display order. */
    public DoctorsToday doctorsToday() {
        LocalDate today = today();
        Map<UUID, List<DoctorWindow>> windows = stats.windowsOn(today).stream()
                .collect(Collectors.groupingBy(DoctorWindow::doctorId));
        Map<UUID, DoctorLoad> loads = stats.loadOn(today);

        List<DoctorToday> result = doctors.findByStatusOrderBySortOrderAscFullNameAsc(DoctorStatus.ACTIVE).stream()
                .filter(d -> windows.containsKey(d.getId()) || loads.containsKey(d.getId()))
                .map(d -> {
                    DoctorLoad load = loads.getOrDefault(d.getId(), new DoctorLoad(0, false));
                    return new DoctorToday(d.getId(), d.getFullName(),
                            merge(windows.getOrDefault(d.getId(), List.of())), load.patients(), load.inVisit());
                })
                .toList();
        return new DoctorsToday(today, result);
    }

    /**
     * DS-06: the {@value #TOP_SERVICES} services with the most requests ({@code counts} is already
     * ordered), then one "Others" entry for the rest when there is any. Shares round half up.
     */
    static List<TopService> topServices(List<ServiceCount> counts, long total) {
        if (total == 0) {
            return List.of();
        }
        List<TopService> top = new ArrayList<>();
        long listed = 0;
        for (ServiceCount service : counts.subList(0, Math.min(TOP_SERVICES, counts.size()))) {
            top.add(new TopService(service.serviceId(), service.serviceName(), service.count(),
                    share(service.count(), total)));
            listed += service.count();
        }
        long rest = total - listed;
        if (rest > 0) {
            top.add(new TopService(null, OTHERS, rest, share(rest, total)));
        }
        return top;
    }

    /** DS-08: windows that overlap or touch become one interval, ordered by start. */
    static List<TimeWindow> merge(List<DoctorWindow> windows) {
        List<LocalTime[]> merged = new ArrayList<>();
        windows.stream()
                .sorted(Comparator.comparing(DoctorWindow::startTime))
                .forEach(w -> {
                    LocalTime[] last = merged.isEmpty() ? null : merged.getLast();
                    if (last != null && !w.startTime().isAfter(last[1])) {
                        if (w.endTime().isAfter(last[1])) {
                            last[1] = w.endTime();
                        }
                    } else {
                        merged.add(new LocalTime[] {w.startTime(), w.endTime()});
                    }
                });
        return merged.stream().map(w -> new TimeWindow(HH_MM.format(w[0]), HH_MM.format(w[1]))).toList();
    }

    private static BigDecimal share(long count, long total) {
        return BigDecimal.valueOf(count * 100).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ClinicTime.ZONE));
    }

    private static Instant startOf(LocalDate day) {
        return day.atStartOfDay(ClinicTime.ZONE).toInstant();
    }
}
