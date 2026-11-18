package com.sokhasaclinic.clinicbackend.appointment.controller;

import com.sokhasaclinic.clinicbackend.appointment.dto.CreateAppointmentRequest;
import com.sokhasaclinic.clinicbackend.appointment.dto.PublicAppointmentCreatedResponse;
import com.sokhasaclinic.clinicbackend.appointment.service.AppointmentService;
import com.sokhasaclinic.clinicbackend.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/appointments")
public class PublicAppointmentController {

    private final AppointmentService appointmentService;

    public PublicAppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PublicAppointmentCreatedResponse> createAppointment(
            @Valid @RequestBody CreateAppointmentRequest request) {
        return ApiResponse.of(appointmentService.createPublicBooking(request));
    }
}
