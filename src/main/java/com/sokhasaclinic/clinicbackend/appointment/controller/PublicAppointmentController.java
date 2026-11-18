package com.sokhasaclinic.clinicbackend.appointment.controller;

import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentDtos.BookingConfirmation;
import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentDtos.BookingPeriodAvailability;
import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentDtos.BookingRequest;
import com.sokhasaclinic.clinicbackend.appointment.exception.AppointmentException;
import com.sokhasaclinic.clinicbackend.appointment.service.AppointmentQueryService;
import com.sokhasaclinic.clinicbackend.appointment.service.AppointmentService;
import com.sokhasaclinic.clinicbackend.appointment.service.AppointmentService.BookingResult;
import com.sokhasaclinic.clinicbackend.appointment.service.AppointmentSnapshot;
import com.sokhasaclinic.clinicbackend.appointment.service.BookingCommand;
import com.sokhasaclinic.clinicbackend.appointment.service.BookingRateLimiter;
import com.sokhasaclinic.clinicbackend.cms.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public booking routes (docs/api-contract.md, "Appointment booking and reception", approved
 * November 18, 2026). Open to guests; SecurityConfig permits exactly these two routes.
 */
@RestController
public class PublicAppointmentController {

    private final AppointmentService appointments;
    private final AppointmentQueryService queries;
    private final BookingRateLimiter rateLimiter;

    public PublicAppointmentController(AppointmentService appointments, AppointmentQueryService queries,
            BookingRateLimiter rateLimiter) {
        this.appointments = appointments;
        this.queries = queries;
        this.rateLimiter = rateLimiter;
    }

    /** AP-11: whether each period on a date still has room for the service. */
    @GetMapping("/api/v1/public/booking-periods")
    public ResponseEntity<ApiResponse<List<BookingPeriodAvailability>>> bookingPeriods(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam UUID serviceId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(queries.periodAvailability(date, serviceId)));
    }

    /**
     * Creates a {@code PENDING} request. {@code Idempotency-Key} is required (AP-03); repeating the
     * same request with the same key returns the first response, also {@code 201}.
     */
    @PostMapping(path = "/api/v1/public/appointments", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<BookingConfirmation>> book(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody BookingRequest body,
            HttpServletRequest request) {
        UUID key = parseKey(idempotencyKey);
        rateLimiter.acquire(ClientAddress.of(request));
        BookingResult result = appointments.request(new BookingCommand(
                body.serviceId(), body.preferredDate(), body.preferredPeriod(), body.language(),
                body.patient().fullName(), body.patient().phone(), body.patient().email(), body.notes()), key);
        AppointmentSnapshot booked = result.appointment();
        return ResponseEntity.created(URI.create("/api/v1/admin/appointments/" + booked.id()))
                .cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(new BookingConfirmation(
                        booked.id(), booked.reference(), booked.status(), booked.preferredDate(),
                        booked.preferredPeriod())));
    }

    private static UUID parseKey(String header) {
        if (header == null || header.isBlank()) {
            throw AppointmentException.idempotencyKeyRequired();
        }
        try {
            return UUID.fromString(header.strip());
        } catch (IllegalArgumentException e) {
            throw AppointmentException.invalidField("Idempotency-Key", "Send a UUID.");
        }
    }
}
