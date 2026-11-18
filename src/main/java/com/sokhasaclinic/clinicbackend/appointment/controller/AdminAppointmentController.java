package com.sokhasaclinic.clinicbackend.appointment.controller;

import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentDtos.AppointmentDetail;
import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentDtos.AppointmentListItem;
import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentDtos.ConfirmRequest;
import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentDtos.DashboardSummary;
import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentDtos.DoctorResponse;
import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentDtos.PageResponse;
import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentDtos.RejectRequest;
import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentDtos.StatusChangeResponse;
import com.sokhasaclinic.clinicbackend.appointment.exception.AppointmentException;
import com.sokhasaclinic.clinicbackend.appointment.model.Actor;
import com.sokhasaclinic.clinicbackend.appointment.model.AppointmentStatus;
import com.sokhasaclinic.clinicbackend.appointment.service.AppointmentQueryService;
import com.sokhasaclinic.clinicbackend.appointment.service.AppointmentQueryService.ListFilter;
import com.sokhasaclinic.clinicbackend.appointment.service.AppointmentQueryService.VersionedDetail;
import com.sokhasaclinic.clinicbackend.appointment.service.AppointmentService;
import com.sokhasaclinic.clinicbackend.appointment.service.ConfirmCommand;
import com.sokhasaclinic.clinicbackend.cms.dto.ApiResponse;
import com.sokhasaclinic.clinicbackend.common.time.ClinicTime;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reception routes (docs/api-contract.md, "Appointment booking and reception", approved November
 * 18, 2026). ADMIN and RECEPTION have the same access (AP-07, SecurityConfig). Every status change
 * needs {@code If-Match} with the detail ETag (AP-08) and returns the new detail and ETag.
 * Bodies are checked by the service after the appointment, version and transition, in contract
 * order, so they carry no {@code @Valid}.
 */
@RestController
public class AdminAppointmentController {

    private static final String BASE = "/api/v1/admin/appointments";
    private static final Pattern ETAG = Pattern.compile("^\"(\\d{1,18})\"$");

    private final AppointmentService appointments;
    private final AppointmentQueryService queries;

    public AdminAppointmentController(AppointmentService appointments, AppointmentQueryService queries) {
        this.appointments = appointments;
        this.queries = queries;
    }

    @GetMapping(BASE)
    public ResponseEntity<PageResponse<AppointmentListItem>> list(
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(queries.list(new ListFilter(status, date, from, to, q), page, size));
    }

    /** {@code id} is a String so a malformed id is a 404, as for services. */
    @GetMapping(BASE + "/{id}")
    public ResponseEntity<ApiResponse<AppointmentDetail>> detail(@PathVariable String id) {
        return versioned(queries.detail(parseId(id)));
    }

    @GetMapping(BASE + "/{id}/history")
    public ResponseEntity<ApiResponse<List<StatusChangeResponse>>> history(@PathVariable String id) {
        List<StatusChangeResponse> changes = appointments.history(parseId(id)).stream()
                .map(change -> new StatusChangeResponse(change.fromStatus(), change.toStatus(), change.actorType(),
                        change.actorName(), change.reason(),
                        change.changedAt().atZone(ClinicTime.ZONE).toOffsetDateTime()))
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ApiResponse<>(changes));
    }

    @PatchMapping(BASE + "/{id}/confirm")
    public ResponseEntity<ApiResponse<AppointmentDetail>> confirm(
            @PathVariable String id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody(required = false) ConfirmRequest body,
            Principal principal) {
        UUID appointmentId = parseId(id);
        ConfirmRequest request = body == null ? new ConfirmRequest(null, null, null, null) : body;
        appointments.confirm(appointmentId, version(ifMatch),
                new ConfirmCommand(request.doctorId(), request.date(), request.startTime(), request.durationMinutes()),
                actor(principal));
        return versioned(queries.detail(appointmentId));
    }

    @PatchMapping(BASE + "/{id}/reject")
    public ResponseEntity<ApiResponse<AppointmentDetail>> reject(
            @PathVariable String id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody(required = false) RejectRequest body,
            Principal principal) {
        UUID appointmentId = parseId(id);
        appointments.reject(appointmentId, version(ifMatch), body == null ? null : body.reason(), actor(principal));
        return versioned(queries.detail(appointmentId));
    }

    @PatchMapping(BASE + "/{id}/arrive")
    public ResponseEntity<ApiResponse<AppointmentDetail>> arrive(
            @PathVariable String id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            Principal principal) {
        UUID appointmentId = parseId(id);
        appointments.arrive(appointmentId, version(ifMatch), actor(principal));
        return versioned(queries.detail(appointmentId));
    }

    @PatchMapping(BASE + "/{id}/start")
    public ResponseEntity<ApiResponse<AppointmentDetail>> start(
            @PathVariable String id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            Principal principal) {
        UUID appointmentId = parseId(id);
        appointments.start(appointmentId, version(ifMatch), actor(principal));
        return versioned(queries.detail(appointmentId));
    }

    @PatchMapping(BASE + "/{id}/complete")
    public ResponseEntity<ApiResponse<AppointmentDetail>> complete(
            @PathVariable String id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            Principal principal) {
        UUID appointmentId = parseId(id);
        appointments.complete(appointmentId, version(ifMatch), actor(principal));
        return versioned(queries.detail(appointmentId));
    }

    @GetMapping("/api/v1/admin/dashboard/summary")
    public ResponseEntity<ApiResponse<DashboardSummary>> summary() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ApiResponse<>(queries.summary()));
    }

    /** AP-16: active doctors for the confirm dialog. */
    @GetMapping("/api/v1/admin/doctors")
    public ResponseEntity<ApiResponse<List<DoctorResponse>>> doctors() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ApiResponse<>(queries.activeDoctors()));
    }

    private static ResponseEntity<ApiResponse<AppointmentDetail>> versioned(VersionedDetail detail) {
        return ResponseEntity.ok()
                .eTag("\"" + detail.version() + "\"")
                .cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(detail.data()));
    }

    private static UUID parseId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw AppointmentException.appointmentNotFound();
        }
    }

    /** Null when If-Match is missing (428 after the existence check); -1 when it is not a version (412). */
    private static Long version(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            return null;
        }
        Matcher matcher = ETAG.matcher(ifMatch.strip());
        return matcher.matches() ? Long.valueOf(matcher.group(1)) : -1L;
    }

    /** The staff member from the Keycloak token: subject, and name or username for the history. */
    static Actor actor(Principal principal) {
        if (principal instanceof JwtAuthenticationToken token) {
            Jwt jwt = token.getToken();
            String name = jwt.getClaimAsString("name");
            if (name == null || name.isBlank()) {
                name = jwt.getClaimAsString("preferred_username");
            }
            return Actor.staff(jwt.getSubject(), name);
        }
        if (principal != null && principal.getName() != null && !principal.getName().isBlank()) {
            return Actor.staff(principal.getName(), null);
        }
        throw new AppointmentException(401, "UNAUTHORIZED", "Sign in to change appointments.");
    }
}
