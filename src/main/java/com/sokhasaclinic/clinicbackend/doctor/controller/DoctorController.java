package com.sokhasaclinic.clinicbackend.doctor.controller;

import com.sokhasaclinic.clinicbackend.common.dto.ApiResponse;
import com.sokhasaclinic.clinicbackend.common.dto.ApiResponsePage;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorRequest;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorResponse;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorScheduleRequest;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorScheduleResponse;
import com.sokhasaclinic.clinicbackend.doctor.model.Doctor;
import com.sokhasaclinic.clinicbackend.doctor.service.DoctorService;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/v1/admin/doctors")
public class DoctorController {

    private final DoctorService doctorService;

    public DoctorController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    @GetMapping
    public ApiResponsePage<DoctorResponse> listDoctors(
            @RequestParam(required = false) Doctor.Status status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return doctorService.listDoctors(status, search, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DoctorResponse> createDoctor(@Valid @RequestBody DoctorRequest request) {
        return ApiResponse.of(doctorService.createDoctor(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<DoctorResponse> getDoctor(@PathVariable UUID id) {
        return ApiResponse.of(doctorService.getDoctorById(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<DoctorResponse> updateDoctor(
            @PathVariable UUID id,
            @Valid @RequestBody DoctorRequest request) {
        return ApiResponse.of(doctorService.updateDoctor(id, request));
    }

    @PatchMapping("/{id}/activate")
    public ApiResponse<DoctorResponse> activateDoctor(@PathVariable UUID id) {
        return ApiResponse.of(doctorService.setDoctorStatus(id, Doctor.Status.ACTIVE));
    }

    @PatchMapping("/{id}/deactivate")
    public ApiResponse<DoctorResponse> deactivateDoctor(@PathVariable UUID id) {
        return ApiResponse.of(doctorService.setDoctorStatus(id, Doctor.Status.INACTIVE));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDoctor(@PathVariable UUID id) {
        doctorService.deleteDoctor(id);
    }

    // --- Schedules ---

    @GetMapping("/{doctorId}/schedules")
    public ApiResponse<List<DoctorScheduleResponse>> listSchedules(@PathVariable UUID doctorId) {
        return ApiResponse.of(doctorService.listDoctorSchedules(doctorId));
    }

    @PostMapping("/{doctorId}/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DoctorScheduleResponse> createSchedule(
            @PathVariable UUID doctorId,
            @Valid @RequestBody DoctorScheduleRequest request) {
        return ApiResponse.of(doctorService.createDoctorSchedule(doctorId, request));
    }

    @PutMapping("/{doctorId}/schedules/{scheduleId}")
    public ApiResponse<DoctorScheduleResponse> updateSchedule(
            @PathVariable UUID doctorId,
            @PathVariable UUID scheduleId,
            @Valid @RequestBody DoctorScheduleRequest request) {
        return ApiResponse.of(doctorService.updateDoctorSchedule(doctorId, scheduleId, request));
    }

    @DeleteMapping("/{doctorId}/schedules/{scheduleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSchedule(
            @PathVariable UUID doctorId,
            @PathVariable UUID scheduleId) {
        doctorService.deleteDoctorSchedule(doctorId, scheduleId);
    }
}
