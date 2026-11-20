package com.sokhasaclinic.clinicbackend.appointment.dto;

import com.sokhasaclinic.clinicbackend.appointment.model.ActorType;
import com.sokhasaclinic.clinicbackend.appointment.model.AppointmentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Request and response bodies of "Appointment booking and reception" (docs/api-contract.md, approved
 * November 18, 2026). Times are ISO 8601 with the clinic offset, for example
 * {@code 2026-11-20T09:30:00+07:00} (BK-08).
 */
public final class AppointmentDtos {

    private AppointmentDtos() {
    }

    /** Body of {@code POST /public/appointments}. The service checks formats, limits and the window. */
    public record BookingRequest(
            @NotNull UUID serviceId,
            @NotNull LocalDate preferredDate,
            String preferredPeriod,
            @NotBlank String language,
            @NotNull @Valid Patient patient,
            @Size(max = 500) String notes,
            @Pattern(regexp = "(?:[01][0-9]|2[0-3]):(?:00|30)")
            String preferredStartTime,
            /** PD-01: optional, non-binding preferred doctor, by public slug. */
            @Size(max = 80) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*")
            String preferredDoctorSlug) {
    }

    public record Patient(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Size(max = 32) String phone,
            @Size(max = 254) String email) {
    }

    /** {@code data} of the 201 response; the patient's details are not echoed back. */
    public record BookingConfirmation(
            UUID id,
            String reference,
            AppointmentStatus status,
            LocalDate preferredDate,
            String preferredPeriod,
            String preferredStartTime,
            /** PT-02: opt-in link for Telegram updates; null when patient updates are disabled. */
            String telegramLinkUrl) {
    }

    public record BookingSlot(OffsetDateTime startAt, OffsetDateTime endAt) { }

    public record BookingSlots(LocalDate date, String timeZone, List<BookingSlot> slots) { }

    /** One period on a date, and whether the service still fits (AP-11). */
    public record BookingPeriodAvailability(String code, String startTime, String endTime, boolean available) {
    }

    /** One row of {@code GET /admin/appointments}. */
    public record AppointmentListItem(
            UUID id,
            String reference,
            AppointmentStatus status,
            String patientName,
            String patientPhone,
            String serviceName,
            LocalDate preferredDate,
            String preferredPeriod,
            String doctorName,
            OffsetDateTime startAt,
            OffsetDateTime createdAt,
            String preferredStartTime,
            /** PD-07: current full name of the patient's preferred doctor; null when none. */
            String preferredDoctorName) {
    }

    /** {@code GET /admin/appointments/{id}}: the list fields and the rest of the appointment. */
    public record AppointmentDetail(
            UUID id,
            String reference,
            AppointmentStatus status,
            String patientName,
            String patientPhone,
            String serviceName,
            LocalDate preferredDate,
            String preferredPeriod,
            String doctorName,
            OffsetDateTime startAt,
            OffsetDateTime createdAt,
            UUID serviceId,
            String patientEmail,
            String language,
            String notes,
            int durationMinutes,
            int bufferMinutes,
            UUID doctorId,
            OffsetDateTime endAt,
            OffsetDateTime updatedAt,
            String preferredStartTime,
            /** PD-03, PD-07: the patient's preferred doctor; never overwritten; null when none. */
            UUID preferredDoctorId,
            String preferredDoctorName,
            /** PT-10: true while the patient receives Telegram updates for this booking. */
            boolean patientTelegramConnected) {
    }

    public record PageInfo(int number, int size, long totalElements, int totalPages) {
    }

    /** A page of results: {@code { "data": [...], "page": {...} }}. */
    public record PageResponse<T>(List<T> data, PageInfo page) {
    }

    public record StatusChangeResponse(
            AppointmentStatus fromStatus,
            AppointmentStatus toStatus,
            ActorType actorType,
            String actorName,
            String reason,
            OffsetDateTime changedAt) {
    }

    /** {@code GET /admin/dashboard/summary}: pending requests and today's appointments by status. */
    public record DashboardSummary(long pending, Map<AppointmentStatus, Long> today) {
    }

    public record DoctorResponse(UUID id, String fullName) {
    }

    /** Body of {@code PATCH .../confirm} (approved confirm rules). Times are local clinic time. */
    public record ConfirmRequest(
            @NotNull UUID doctorId,
            @NotNull LocalDate date,
            @NotNull LocalTime startTime,
            @Min(5) @Max(480) Integer durationMinutes) {
    }

    /** Body of {@code PATCH .../reject}. The reason is required, 1ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã¢â‚¬Å“500 characters after trimming. */
    public record RejectRequest(@NotBlank @Size(max = 500) String reason) {
    }
}
