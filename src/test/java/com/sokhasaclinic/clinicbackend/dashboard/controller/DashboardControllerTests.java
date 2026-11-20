package com.sokhasaclinic.clinicbackend.dashboard.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sokhasaclinic.clinicbackend.appointment.controller.AppointmentExceptionHandler;
import com.sokhasaclinic.clinicbackend.dashboard.dto.DashboardDtos.DashboardStats;
import com.sokhasaclinic.clinicbackend.dashboard.dto.DashboardDtos.DoctorToday;
import com.sokhasaclinic.clinicbackend.dashboard.dto.DashboardDtos.DoctorsToday;
import com.sokhasaclinic.clinicbackend.dashboard.dto.DashboardDtos.StatsBucket;
import com.sokhasaclinic.clinicbackend.dashboard.dto.DashboardDtos.TimeWindow;
import com.sokhasaclinic.clinicbackend.dashboard.dto.DashboardDtos.TopService;
import com.sokhasaclinic.clinicbackend.dashboard.service.DashboardStatsService;
import com.sokhasaclinic.clinicbackend.dashboard.service.StatsRange;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Response shapes and range validation of the dashboard statistics routes (DS-01, DS-08, DS-11). */
class DashboardControllerTests {

    private DashboardStatsService dashboard;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        dashboard = mock(DashboardStatsService.class);
        mvc = MockMvcBuilders.standaloneSetup(new DashboardController(dashboard))
                .setControllerAdvice(new AppointmentExceptionHandler())
                .build();
    }

    @Test
    void statsReturnTheSeriesAndTopServicesUncached() throws Exception {
        when(dashboard.stats(StatsRange.MONTH)).thenReturn(new DashboardStats(StatsRange.MONTH,
                LocalDate.of(2025, 12, 1), LocalDate.of(2026, 11, 20),
                List.of(new StatsBucket(LocalDate.of(2026, 11, 1), 12, 9, 7)), 12,
                List.of(new TopService(null, "Others", 12, new BigDecimal("100.0")))));

        mvc.perform(get("/api/v1/admin/dashboard/stats").param("range", "MONTH"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.range").value("MONTH"))
                .andExpect(jsonPath("$.data.from").value("2025-12-01"))
                .andExpect(jsonPath("$.data.to").value("2026-11-20"))
                .andExpect(jsonPath("$.data.series[0].periodStart").value("2026-11-01"))
                .andExpect(jsonPath("$.data.series[0].requested").value(12))
                .andExpect(jsonPath("$.data.series[0].confirmed").value(9))
                .andExpect(jsonPath("$.data.series[0].completed").value(7))
                .andExpect(jsonPath("$.data.totalRequested").value(12))
                .andExpect(jsonPath("$.data.topServices[0].serviceId").isEmpty())
                .andExpect(jsonPath("$.data.topServices[0].share").value(100.0));
    }

    @Test
    void missingRangeIsAValidationError() throws Exception {
        mvc.perform(get("/api/v1/admin/dashboard/stats"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("range"));
    }

    @Test
    void unknownRangeIsAValidationError() throws Exception {
        mvc.perform(get("/api/v1/admin/dashboard/stats").param("range", "DAY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("range"));
    }

    @Test
    void doctorsTodayReturnsWindowsAndCounts() throws Exception {
        UUID id = UUID.fromString("9a7e1c2b-3d4e-4f5a-8b6c-7d8e9f0a1b2c");
        when(dashboard.doctorsToday()).thenReturn(new DoctorsToday(LocalDate.of(2026, 11, 20), List.of(
                new DoctorToday(id, "Dr. Sok Vanna", List.of(new TimeWindow("08:00", "12:00")), 3, false))));

        mvc.perform(get("/api/v1/admin/dashboard/doctors-today"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.date").value("2026-11-20"))
                .andExpect(jsonPath("$.data.doctors[0].id").value(id.toString()))
                .andExpect(jsonPath("$.data.doctors[0].windows[0].startTime").value("08:00"))
                .andExpect(jsonPath("$.data.doctors[0].windows[0].endTime").value("12:00"))
                .andExpect(jsonPath("$.data.doctors[0].patients").value(3))
                .andExpect(jsonPath("$.data.doctors[0].inVisit").value(false));
    }
}
