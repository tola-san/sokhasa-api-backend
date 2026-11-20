package com.sokhasaclinic.clinicbackend.dashboard.controller;

import com.sokhasaclinic.clinicbackend.cms.dto.ApiResponse;
import com.sokhasaclinic.clinicbackend.dashboard.dto.DashboardDtos.DashboardStats;
import com.sokhasaclinic.clinicbackend.dashboard.dto.DashboardDtos.DoctorsToday;
import com.sokhasaclinic.clinicbackend.dashboard.service.DashboardStatsService;
import com.sokhasaclinic.clinicbackend.dashboard.service.StatsRange;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard statistics revision (DS-01 to DS-12, approved November 20, 2026). ADMIN and RECEPTION,
 * as for the summary (DS-11, SecurityConfig). A missing or unknown {@code range} is a
 * {@code 400 VALIDATION_ERROR} on field {@code range} from AppointmentExceptionHandler.
 */
@RestController
public class DashboardController {

    private final DashboardStatsService dashboard;

    public DashboardController(DashboardStatsService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/api/v1/admin/dashboard/stats")
    public ResponseEntity<ApiResponse<DashboardStats>> stats(@RequestParam StatsRange range) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ApiResponse<>(dashboard.stats(range)));
    }

    @GetMapping("/api/v1/admin/dashboard/doctors-today")
    public ResponseEntity<ApiResponse<DoctorsToday>> doctorsToday() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ApiResponse<>(dashboard.doctorsToday()));
    }
}
