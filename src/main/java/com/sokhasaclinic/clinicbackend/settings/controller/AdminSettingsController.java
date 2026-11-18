package com.sokhasaclinic.clinicbackend.settings.controller;

import com.sokhasaclinic.clinicbackend.common.dto.ApiResponse;
import com.sokhasaclinic.clinicbackend.settings.dto.ClinicSettingsDto;
import com.sokhasaclinic.clinicbackend.settings.dto.WorkingHoursDto;
import com.sokhasaclinic.clinicbackend.settings.service.ClinicSettingsService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/settings")
public class AdminSettingsController {

    private final ClinicSettingsService settingsService;

    public AdminSettingsController(ClinicSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/clinic")
    public ApiResponse<ClinicSettingsDto> getClinicSettings() {
        return ApiResponse.of(settingsService.getClinicSettings());
    }

    @PutMapping("/clinic")
    public ApiResponse<ClinicSettingsDto> updateClinicSettings(@Valid @RequestBody ClinicSettingsDto dto) {
        return ApiResponse.of(settingsService.updateClinicSettings(dto));
    }

    @GetMapping("/working-hours")
    public ApiResponse<List<WorkingHoursDto>> getWorkingHours() {
        return ApiResponse.of(settingsService.getWorkingHours());
    }

    @PutMapping("/working-hours")
    public ApiResponse<List<WorkingHoursDto>> updateWorkingHours(@Valid @RequestBody List<WorkingHoursDto> dtos) {
        return ApiResponse.of(settingsService.updateWorkingHours(dtos));
    }
}
