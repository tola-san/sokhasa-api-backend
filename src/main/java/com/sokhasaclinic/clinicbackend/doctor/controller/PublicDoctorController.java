package com.sokhasaclinic.clinicbackend.doctor.controller;

import com.sokhasaclinic.clinicbackend.common.dto.ApiResponse;
import com.sokhasaclinic.clinicbackend.doctor.dto.AvailabilityResponse;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorResponse;
import com.sokhasaclinic.clinicbackend.doctor.service.DoctorService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
public class PublicDoctorController {

    private final DoctorService doctorService;

    public PublicDoctorController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    @GetMapping("/doctors")
    public ApiResponse<List<DoctorResponse>> listDoctors() {
        return ApiResponse.of(doctorService.listPublicDoctors());
    }

    @GetMapping("/doctors/{id}")
    public ApiResponse<DoctorResponse> getDoctor(@PathVariable UUID id) {
        return ApiResponse.of(doctorService.getDoctorById(id));
    }

    @GetMapping("/appointments/availability")
    public ApiResponse<AvailabilityResponse> checkAvailability(
            @RequestParam UUID serviceId,
            @RequestParam(required = false) UUID doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.of(doctorService.checkAvailability(serviceId, doctorId, date));
    }
}
