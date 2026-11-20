package com.sokhasaclinic.clinicbackend.dashboard.dto;

import com.sokhasaclinic.clinicbackend.dashboard.service.StatsRange;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Responses of the dashboard statistics revision (DS-01 to DS-12). Totals only, no patient data. */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    /** {@code GET /admin/dashboard/stats}. */
    public record DashboardStats(
            StatsRange range,
            LocalDate from,
            LocalDate to,
            List<StatsBucket> series,
            long totalRequested,
            List<TopService> topServices) {
    }

    /** One day, month or year; {@code periodStart} is its first day. */
    public record StatsBucket(LocalDate periodStart, long requested, long confirmed, long completed) {
    }

    /** {@code serviceId} is null for the {@code Others} entry. {@code share} has one decimal. */
    public record TopService(UUID serviceId, String serviceName, long count, BigDecimal share) {
    }

    /** {@code GET /admin/dashboard/doctors-today}. */
    public record DoctorsToday(LocalDate date, List<DoctorToday> doctors) {
    }

    public record DoctorToday(UUID id, String fullName, List<TimeWindow> windows, long patients, boolean inVisit) {
    }

    /** Clinic-local {@code HH:mm}. */
    public record TimeWindow(String startTime, String endTime) {
    }
}
