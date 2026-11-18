package com.sokhasaclinic.clinicbackend.settings.controller;

import com.sokhasaclinic.clinicbackend.common.dto.ApiResponse;
import com.sokhasaclinic.clinicbackend.settings.dto.ClinicSettingsDto;
import com.sokhasaclinic.clinicbackend.settings.service.ClinicSettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/settings")
public class PublicSettingsController {

    private final ClinicSettingsService settingsService;

    public PublicSettingsController(ClinicSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/clinic")
    public ApiResponse<ClinicSettingsDto> getPublicClinicInfo() {
        return ApiResponse.of(settingsService.getClinicSettings());
    }
}
