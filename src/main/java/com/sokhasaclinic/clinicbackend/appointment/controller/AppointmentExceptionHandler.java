package com.sokhasaclinic.clinicbackend.appointment.controller;

import com.sokhasaclinic.clinicbackend.appointment.exception.AppointmentException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Problem-details errors for the appointment routes, in the same shape as the rest of the API
 * ({@code type}, {@code title}, {@code status}, {@code detail}, {@code instance}, {@code timestamp},
 * {@code errors}).
 */
@RestControllerAdvice(basePackages = {"com.sokhasaclinic.clinicbackend.appointment.controller",
        "com.sokhasaclinic.clinicbackend.doctor.controller",
        "com.sokhasaclinic.clinicbackend.dashboard.controller"})
public class AppointmentExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AppointmentExceptionHandler.class);

    @ExceptionHandler(AppointmentException.class)
    public ResponseEntity<Map<String, Object>> handleAppointment(AppointmentException ex, HttpServletRequest request) {
        return problem(ex.getStatus(), ex.getType(), ex.getMessage(), ex.getErrors(), request);
    }

    /** Bean Validation on a request body: one entry per invalid field, such as {@code patient.phone}. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidBody(MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        List<Map<String, Object>> errors = ex.getBindingResult().getFieldErrors().stream()
                .<Map<String, Object>>map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() == null ? "Invalid value." : error.getDefaultMessage()))
                .toList();
        return problem(400, "VALIDATION_ERROR", "One or more fields are invalid.", errors, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        return problem(400, "VALIDATION_ERROR", "One or more fields are invalid.",
                List.of(Map.of("field", ex.getName(), "message", "Invalid value.")), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParameter(MissingServletRequestParameterException ex,
            HttpServletRequest request) {
        return problem(400, "VALIDATION_ERROR", "One or more fields are invalid.",
                List.of(Map.of("field", ex.getParameterName(), "message", "Required.")), request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(Exception ex, HttpServletRequest request) {
        return problem(400, "VALIDATION_ERROR", "The request body is not valid JSON for this route.", List.of(), request);
    }

    private static ResponseEntity<Map<String, Object>> problem(int status, String type, String detail,
            List<Map<String, Object>> errors, HttpServletRequest request) {
        if (status >= 500) {
            log.error("Appointments {} {} failed: {} {}", request.getMethod(), request.getRequestURI(), type, detail);
        }
        HttpStatus httpStatus = HttpStatus.resolve(status);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type);
        body.put("title", httpStatus != null ? httpStatus.getReasonPhrase() : "Error");
        body.put("status", status);
        body.put("detail", detail);
        body.put("instance", request.getRequestURI());
        body.put("timestamp", Instant.now());
        body.put("errors", errors);
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }
}
