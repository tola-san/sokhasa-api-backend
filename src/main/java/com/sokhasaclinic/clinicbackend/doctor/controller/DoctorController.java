package com.sokhasaclinic.clinicbackend.doctor.controller;

import com.sokhasaclinic.clinicbackend.appointment.exception.AppointmentException;
import com.sokhasaclinic.clinicbackend.cms.dto.ApiResponse;
import com.sokhasaclinic.clinicbackend.doctor.dto.BookingWindowDtos;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorRequest;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorResponse;
import com.sokhasaclinic.clinicbackend.doctor.service.DoctorService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** ADMIN management. The reception-facing GET /admin/doctors remains in AdminAppointmentController. */
@RestController
@RequestMapping("/api/v1/admin/doctors")
public class DoctorController {
    private static final String BASE = "/api/v1/admin/doctors/";
    private final DoctorService service;

    public DoctorController(DoctorService service) { this.service = service; }

    @GetMapping("/manage")
    public ResponseEntity<ApiResponse<List<DoctorResponse>>> list() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ApiResponse<>(service.list()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DoctorResponse>> create(@Valid @RequestBody DoctorRequest request) {
        DoctorResponse data = service.create(request);
        return ResponseEntity.created(URI.create(BASE + data.id())).cacheControl(CacheControl.noStore())
                .eTag(etag(data.version())).body(new ApiResponse<>(data));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DoctorResponse>> detail(@PathVariable String id) {
        return doctor(service.detail(doctorId(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DoctorResponse>> update(@PathVariable String id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody DoctorRequest request) {
        return doctor(service.update(doctorId(id), ifMatch, request));
    }

    @GetMapping("/{id}/booking-windows")
    public ResponseEntity<ApiResponse<List<BookingWindowDtos.Response>>> windows(@PathVariable String id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID serviceId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(service.windows(doctorId(id), from, to, serviceId)));
    }

    @PostMapping("/{id}/booking-windows")
    public ResponseEntity<ApiResponse<BookingWindowDtos.Response>> createWindow(@PathVariable String id,
            @Valid @RequestBody BookingWindowDtos.Request request) {
        BookingWindowDtos.Response data = service.createWindow(doctorId(id), request);
        return ResponseEntity.created(URI.create(BASE + data.doctorId() + "/booking-windows/" + data.id()))
                .cacheControl(CacheControl.noStore()).eTag(etag(data.version())).body(new ApiResponse<>(data));
    }

    @GetMapping("/{id}/booking-windows/{windowId}")
    public ResponseEntity<ApiResponse<BookingWindowDtos.Response>> window(@PathVariable String id, @PathVariable String windowId) {
        return window(service.window(doctorId(id), windowId(windowId)));
    }

    @PutMapping("/{id}/booking-windows/{windowId}")
    public ResponseEntity<ApiResponse<BookingWindowDtos.Response>> updateWindow(@PathVariable String id,
            @PathVariable String windowId, @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody BookingWindowDtos.Request request) {
        return window(service.updateWindow(doctorId(id), windowId(windowId), ifMatch, request));
    }

    @DeleteMapping("/{id}/booking-windows/{windowId}")
    public ResponseEntity<Void> deleteWindow(@PathVariable String id, @PathVariable String windowId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        service.deleteWindow(doctorId(id), windowId(windowId), ifMatch);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static ResponseEntity<ApiResponse<DoctorResponse>> doctor(DoctorResponse data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).eTag(etag(data.version())).body(new ApiResponse<>(data));
    }

    private static ResponseEntity<ApiResponse<BookingWindowDtos.Response>> window(BookingWindowDtos.Response data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).eTag(etag(data.version())).body(new ApiResponse<>(data));
    }

    private static String etag(long version) { return "\"" + version + "\""; }
    private static UUID doctorId(String id) {
        try { return UUID.fromString(id); } catch (IllegalArgumentException ex) { throw AppointmentException.doctorNotFound(); }
    }
    private static UUID windowId(String id) {
        try { return UUID.fromString(id); } catch (IllegalArgumentException ex) { throw DoctorService.windowNotFound(); }
    }
}
