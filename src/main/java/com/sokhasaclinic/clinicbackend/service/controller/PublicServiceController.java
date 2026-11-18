package com.sokhasaclinic.clinicbackend.service.controller;

import com.sokhasaclinic.clinicbackend.common.dto.ApiResponse;
import com.sokhasaclinic.clinicbackend.service.dto.ServiceResponse;
import com.sokhasaclinic.clinicbackend.service.service.ClinicServiceService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/services")
public class PublicServiceController {

    private final ClinicServiceService serviceService;

    public PublicServiceController(ClinicServiceService serviceService) {
        this.serviceService = serviceService;
    }

    @GetMapping
    public ApiResponse<List<ServiceResponse>> listServices() {
        return ApiResponse.of(serviceService.listPublicServices());
    }

    @GetMapping("/{id}")
    public ApiResponse<ServiceResponse> getService(@PathVariable UUID id) {
        return ApiResponse.of(serviceService.getServiceById(id));
    }
}
