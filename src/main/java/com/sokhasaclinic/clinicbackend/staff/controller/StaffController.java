package com.sokhasaclinic.clinicbackend.staff.controller;

import com.sokhasaclinic.clinicbackend.common.dto.ApiResponse;
import com.sokhasaclinic.clinicbackend.staff.dto.StaffRequest;
import com.sokhasaclinic.clinicbackend.staff.dto.StaffResponse;
import com.sokhasaclinic.clinicbackend.staff.dto.UpdateStaffRolesRequest;
import com.sokhasaclinic.clinicbackend.staff.service.StaffService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/staff")
public class StaffController {

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @GetMapping
    public ApiResponse<List<StaffResponse>> listStaff() {
        return ApiResponse.of(staffService.listStaff());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StaffResponse> createStaff(@Valid @RequestBody StaffRequest request) {
        return ApiResponse.of(staffService.createStaff(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<StaffResponse> getStaff(@PathVariable UUID id) {
        return ApiResponse.of(staffService.getStaff(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<StaffResponse> updateStaff(
            @PathVariable UUID id,
            @Valid @RequestBody StaffRequest request) {
        return ApiResponse.of(staffService.updateStaff(id, request));
    }

    @PatchMapping("/{id}/activate")
    public ApiResponse<StaffResponse> activateStaff(@PathVariable UUID id) {
        return ApiResponse.of(staffService.setStaffActive(id, true));
    }

    @PatchMapping("/{id}/deactivate")
    public ApiResponse<StaffResponse> deactivateStaff(@PathVariable UUID id) {
        return ApiResponse.of(staffService.setStaffActive(id, false));
    }

    @PatchMapping("/{id}/roles")
    public ApiResponse<StaffResponse> updateRoles(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStaffRolesRequest request) {
        return ApiResponse.of(staffService.updateStaffRoles(id, request));
    }
}
