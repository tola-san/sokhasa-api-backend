package com.sokhasaclinic.clinicbackend.appointment.exception;

import com.sokhasaclinic.clinicbackend.appointment.model.AppointmentStatus;
import java.util.List;
import java.util.Map;

/**
 * Domain error for the booking workflow. {@code status} and {@code type} become the problem-details
 * HTTP status and code once the appointment routes are added.
 */
public class AppointmentException extends RuntimeException {

    private final int status;
    private final String type;
    private final List<Map<String, Object>> errors;

    public AppointmentException(int status, String type, String message) {
        this(status, type, message, List.of());
    }

    public AppointmentException(int status, String type, String message, List<Map<String, Object>> errors) {
        super(message);
        this.status = status;
        this.type = type;
        this.errors = List.copyOf(errors);
    }

    public int getStatus() {
        return status;
    }

    public String getType() {
        return type;
    }

    public List<Map<String, Object>> getErrors() {
        return errors;
    }

    /** A {@code 400 VALIDATION_ERROR} naming the invalid field. */
    public static AppointmentException invalidField(String field, String message) {
        return new AppointmentException(400, "VALIDATION_ERROR", message,
                List.of(Map.of("field", field, "message", message)));
    }

    /** AP-13: specific not-found codes. */
    public static AppointmentException appointmentNotFound() {
        return new AppointmentException(404, "APPOINTMENT_NOT_FOUND", "Appointment not found.");
    }

    public static AppointmentException serviceNotFound() {
        return new AppointmentException(404, "SERVICE_NOT_FOUND", "Service not found or not bookable.");
    }

    public static AppointmentException doctorNotFound() {
        return new AppointmentException(404, "DOCTOR_NOT_FOUND", "Doctor not found or not active.");
    }

    public static AppointmentException versionRequired() {
        return new AppointmentException(428, "VERSION_REQUIRED", "If-Match is required.");
    }

    public static AppointmentException idempotencyKeyRequired() {
        return new AppointmentException(428, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key is required.");
    }

    /** AP-03: the same key was sent with a different request body. */
    public static AppointmentException idempotencyConflict() {
        return new AppointmentException(409, "IDEMPOTENCY_CONFLICT",
                "This Idempotency-Key was already used for a different request.");
    }

    /** AP-12. */
    public static AppointmentException rateLimited() {
        return new AppointmentException(429, "RATE_LIMITED", "Too many booking requests. Try again later.");
    }

    public static AppointmentException invalidTransition(AppointmentStatus from, AppointmentStatus to) {
        return new AppointmentException(409, "INVALID_TRANSITION",
                "An appointment cannot move from " + from + " to " + to + ".");
    }

    public static AppointmentException periodFull() {
        return new AppointmentException(409, "PERIOD_FULL",
                "The requested date and period have no remaining capacity.");
    }

    public static AppointmentException slotUnavailable() {
        return new AppointmentException(409, "SLOT_UNAVAILABLE", "That time is no longer available. Choose another time.");
    }

    public static AppointmentException doctorUnavailable() {
        return new AppointmentException(409, "DOCTOR_UNAVAILABLE",
                "The doctor already has an overlapping appointment.");
    }

    public static AppointmentException versionMismatch() {
        return new AppointmentException(412, "VERSION_MISMATCH", "Reload the appointment before changing it.");
    }
}
