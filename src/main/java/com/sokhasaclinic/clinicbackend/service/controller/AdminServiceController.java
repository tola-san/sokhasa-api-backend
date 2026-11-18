package com.sokhasaclinic.clinicbackend.service.controller;

import com.sokhasaclinic.clinicbackend.common.dto.ApiResponse;
import com.sokhasaclinic.clinicbackend.common.dto.ApiResponsePage;
import com.sokhasaclinic.clinicbackend.service.dto.ServiceRequest;
import com.sokhasaclinic.clinicbackend.service.dto.ServiceResponse;
import com.sokhasaclinic.clinicbackend.service.model.ClinicService;
import com.sokhasaclinic.clinicbackend.service.service.ClinicServiceService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/services")
public class AdminServiceController {

    private final ClinicServiceService serviceService;

    public AdminServiceController(ClinicServiceService serviceService) {
        this.serviceService = serviceService;
    }

    @GetMapping
    public ApiResponsePage<ServiceResponse> listServices(
            @RequestParam(required = false) ClinicService.Status status,
            @PageableDefault(size = 20) Pageable pageable) {
        return serviceService.listServices(status, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ServiceResponse> createService(@Valid @RequestBody ServiceRequest request) {
        return ApiResponse.of(serviceService.createService(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<ServiceResponse> getService(@PathVariable UUID id) {
        return ApiResponse.of(serviceService.getServiceById(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<ServiceResponse> updateService(
            @PathVariable UUID id,
            @Valid @RequestBody ServiceRequest request) {
        return ApiResponse.of(serviceService.updateService(id, request));
    }

    @PatchMapping("/{id}/activate")
    public ApiResponse<ServiceResponse> activateService(@PathVariable UUID id) {
        return ApiResponse.of(serviceService.setServiceStatus(id, ClinicService.Status.ACTIVE));
    }

    @PatchMapping("/{id}/deactivate")
    public ApiResponse<ServiceResponse> deactivateService(@PathVariable UUID id) {
        return ApiResponse.of(serviceService.setServiceStatus(id, ClinicService.Status.INACTIVE));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteService(@PathVariable UUID id) {
        serviceService.deleteService(id);
    }
}
