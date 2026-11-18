package com.sokhasaclinic.clinicbackend.clinicservice.controller;

import com.sokhasaclinic.clinicbackend.clinicservice.dto.AdminServiceDtos.AdminService;
import com.sokhasaclinic.clinicbackend.clinicservice.dto.AdminServiceDtos.AdminServiceUpdate;
import com.sokhasaclinic.clinicbackend.clinicservice.dto.AdminServiceDtos.VersionedService;
import com.sokhasaclinic.clinicbackend.clinicservice.model.ServiceStatus;
import com.sokhasaclinic.clinicbackend.clinicservice.service.ServiceAdminService;
import com.sokhasaclinic.clinicbackend.cms.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin editing of clinic services (phase 1). {@code /api/v1/admin/**} requires the ADMIN role
 * (SecurityConfig, decisions SA-01 and SA-09). Every write needs {@code If-Match}.
 */
@RestController
@RequestMapping("/api/v1/admin/services")
public class AdminServiceController {

    private final ServiceAdminService adminService;

    public AdminServiceController(ServiceAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminService>>> listServices() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(adminService.listServices()));
    }

    /** {@code id} is a String so a malformed id is a 404, not a 400. */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminService>> getService(@PathVariable String id) {
        return versioned(adminService.getService(id));
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<AdminService>> updateService(
            @PathVariable String id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody AdminServiceUpdate update) {
        return versioned(adminService.updateService(id, ifMatch, update));
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<AdminService>> activate(
            @PathVariable String id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return versioned(adminService.changeStatus(id, ifMatch, ServiceStatus.ACTIVE));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<AdminService>> deactivate(
            @PathVariable String id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return versioned(adminService.changeStatus(id, ifMatch, ServiceStatus.INACTIVE));
    }

    private static ResponseEntity<ApiResponse<AdminService>> versioned(VersionedService service) {
        return ResponseEntity.ok()
                .eTag("\"" + service.version() + "\"")
                .cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(service.data()));
    }
}
