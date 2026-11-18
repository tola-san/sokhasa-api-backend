package com.sokhasaclinic.clinicbackend.appointment.controller;

import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentDetailResponse;
import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentStatusHistoryDto;
import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentSummaryResponse;
import com.sokhasaclinic.clinicbackend.appointment.dto.AssignDoctorRequest;
import com.sokhasaclinic.clinicbackend.appointment.dto.ConfirmAppointmentRequest;
import com.sokhasaclinic.clinicbackend.appointment.dto.RejectAppointmentRequest;
import com.sokhasaclinic.clinicbackend.appointment.dto.RescheduleAppointmentRequest;
import com.sokhasaclinic.clinicbackend.appointment.model.Appointment;
import com.sokhasaclinic.clinicbackend.appointment.service.AppointmentService;
import com.sokhasaclinic.clinicbackend.common.dto.ApiResponse;
import com.sokhasaclinic.clinicbackend.common.dto.ApiResponsePage;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/appointments")
public class AdminAppointmentController {

    private final AppointmentService appointmentService;

    public AdminAppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping
    public ApiResponsePage<AppointmentSummaryResponse> listAppointments(
            @RequestParam(required = false) Appointment.Status status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) UUID doctorId,
            @RequestParam(required = false) UUID serviceId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {

        return appointmentService.listAppointments(
                status, date, dateFrom, dateTo, doctorId, serviceId, search, pageable
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<AppointmentDetailResponse> getAppointment(@PathVariable UUID id) {
        return ApiResponse.of(appointmentService.getAppointmentDetails(id));
    }

    @PatchMapping("/{id}/assign-doctor")
    public ApiResponse<AppointmentDetailResponse> assignDoctor(
            @PathVariable UUID id,
            @Valid @RequestBody AssignDoctorRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt != null ? jwt.getSubject() : "system";
        String username = extractUsername(jwt);
        return ApiResponse.of(appointmentService.assignDoctor(id, request, userId, username));
    }

    @PatchMapping("/{id}/confirm")
    public ApiResponse<AppointmentDetailResponse> confirmAppointment(
            @PathVariable UUID id,
            @RequestBody(required = false) ConfirmAppointmentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt != null ? jwt.getSubject() : "system";
        String username = extractUsername(jwt);
        ConfirmAppointmentRequest req = request != null ? request : new ConfirmAppointmentRequest(null, null);
        return ApiResponse.of(appointmentService.confirmAppointment(id, req, userId, username));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<AppointmentDetailResponse> updateVisitStatus(
            @PathVariable UUID id,
            @Valid @RequestBody com.sokhasaclinic.clinicbackend.appointment.dto.UpdateAppointmentStatusRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt != null ? jwt.getSubject() : "system";
        String username = extractUsername(jwt);
        return ApiResponse.of(appointmentService.updateVisitStatus(id, request, userId, username));
    }

    @PatchMapping("/{id}/reject")
    public ApiResponse<AppointmentDetailResponse> rejectAppointment(
            @PathVariable UUID id,
            @Valid @RequestBody RejectAppointmentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt != null ? jwt.getSubject() : "system";
        String username = extractUsername(jwt);
        return ApiResponse.of(appointmentService.rejectAppointment(id, request, userId, username));
    }

    @PatchMapping("/{id}/reschedule")
    public ApiResponse<AppointmentDetailResponse> rescheduleAppointment(
            @PathVariable UUID id,
            @Valid @RequestBody RescheduleAppointmentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt != null ? jwt.getSubject() : "system";
        String username = extractUsername(jwt);
        return ApiResponse.of(appointmentService.rescheduleAppointment(id, request, userId, username));
    }

    @GetMapping("/{id}/history")
    public ApiResponse<List<AppointmentStatusHistoryDto>> getHistory(@PathVariable UUID id) {
        return ApiResponse.of(appointmentService.getAppointmentHistory(id));
    }

    private String extractUsername(Jwt jwt) {
        if (jwt == null) return "staff";
        String username = jwt.getClaimAsString("preferred_username");
        return username != null && !username.isBlank() ? username : jwt.getSubject();
    }
}
