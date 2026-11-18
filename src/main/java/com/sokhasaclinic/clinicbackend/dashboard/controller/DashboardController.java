package com.sokhasaclinic.clinicbackend.dashboard.controller;

import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentStatusHistoryDto;
import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentSummaryResponse;
import com.sokhasaclinic.clinicbackend.common.dto.ApiResponse;
import com.sokhasaclinic.clinicbackend.dashboard.dto.DashboardOverviewResponse;
import com.sokhasaclinic.clinicbackend.dashboard.service.DashboardService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/overview")
    public ApiResponse<DashboardOverviewResponse> getOverview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.of(dashboardService.getOverview(date));
    }

    @GetMapping("/upcoming-appointments")
    public ApiResponse<List<AppointmentSummaryResponse>> getUpcomingAppointments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.of(dashboardService.getUpcomingAppointments(date, limit));
    }

    @GetMapping("/recent-activities")
    public ApiResponse<List<AppointmentStatusHistoryDto>> getRecentActivities(
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.of(dashboardService.getRecentActivities(limit));
    }
}
