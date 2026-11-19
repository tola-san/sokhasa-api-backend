package com.sokhasaclinic.clinicbackend.doctor.service;

import com.sokhasaclinic.clinicbackend.appointment.exception.AppointmentException;
import com.sokhasaclinic.clinicbackend.appointment.repository.BookingSettings;
import com.sokhasaclinic.clinicbackend.clinicservice.model.ServiceStatus;
import com.sokhasaclinic.clinicbackend.clinicservice.repository.ClinicServiceRepository;
import com.sokhasaclinic.clinicbackend.common.time.ClinicTime;
import com.sokhasaclinic.clinicbackend.doctor.dto.BookingWindowDtos;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorRequest;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorResponse;
import com.sokhasaclinic.clinicbackend.doctor.model.Doctor;
import com.sokhasaclinic.clinicbackend.doctor.model.DoctorBookingWindow;
import com.sokhasaclinic.clinicbackend.doctor.model.DoctorStatus;
import com.sokhasaclinic.clinicbackend.doctor.repository.DoctorBookingWindowRepository;
import com.sokhasaclinic.clinicbackend.doctor.repository.DoctorRepository;
import com.sokhasaclinic.clinicbackend.doctor.repository.DoctorScheduleChecks;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resource management: doctor first, then window locks, shared with booking/confirmation. */
@Service
@Transactional(readOnly = true)
public class DoctorService {
    private final DoctorRepository doctors;
    private final DoctorBookingWindowRepository windows;
    private final DoctorScheduleChecks checks;
    private final ClinicServiceRepository services;
    private final BookingSettings settings;

    public DoctorService(DoctorRepository doctors, DoctorBookingWindowRepository windows,
            DoctorScheduleChecks checks, ClinicServiceRepository services, BookingSettings settings) {
        this.doctors = doctors;
        this.windows = windows;
        this.checks = checks;
        this.services = services;
        this.settings = settings;
    }

    public List<DoctorResponse> list() {
        return doctors.findAllByOrderBySortOrderAscFullNameAscIdAsc().stream().map(DoctorService::response).toList();
    }

    public DoctorResponse detail(UUID id) { return response(find(id)); }

    @Transactional
    public DoctorResponse create(DoctorRequest request) {
        validate(request);
        return response(doctors.saveAndFlush(Doctor.create(request.fullName().strip(), request.status(),
                request.sortOrder(), Instant.now())));
    }

    @Transactional
    public DoctorResponse update(UUID id, String ifMatch, DoctorRequest request) {
        Doctor doctor = lock(id);
        version(ifMatch, doctor.getVersion());
        validate(request);
        Instant now = Instant.now();
        if (doctor.isActive() && request.status() == DoctorStatus.INACTIVE && checks.hasCommitments(id, now)) {
            throw conflict("DOCTOR_IN_USE", "Resolve the doctor's pending reservations and outstanding visits before deactivation.");
        }
        doctor.update(request.fullName().strip(), request.status(), request.sortOrder(), now);
        return response(doctors.saveAndFlush(doctor));
    }

    public List<BookingWindowDtos.Response> windows(UUID doctorId, LocalDate from, LocalDate to, UUID serviceId) {
        find(doctorId);
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= 62) {
            throw AppointmentException.invalidField("to", "Supply an inclusive date range of at most 62 days.");
        }
        return windows.list(doctorId, from, to, serviceId).stream().map(DoctorService::response).toList();
    }

    public BookingWindowDtos.Response window(UUID doctorId, UUID windowId) {
        find(doctorId);
        return response(windows.findByIdAndDoctorId(windowId, doctorId).orElseThrow(DoctorService::windowNotFound));
    }

    @Transactional
    public BookingWindowDtos.Response createWindow(UUID doctorId, BookingWindowDtos.Request request) {
        Doctor doctor = lock(doctorId);
        Times times = validateWindow(doctor, request);
        UUID id = UUID.randomUUID(); // An excluded ID that cannot match an existing window.
        overlap(doctorId, id, request, times);
        return response(windows.saveAndFlush(DoctorBookingWindow.create(doctorId, request.serviceId(), request.date(),
                times.start(), times.end())));
    }

    @Transactional
    public BookingWindowDtos.Response updateWindow(UUID doctorId, UUID windowId, String ifMatch,
            BookingWindowDtos.Request request) {
        Doctor doctor = lock(doctorId);
        DoctorBookingWindow window = lockWindow(doctorId, windowId);
        version(ifMatch, window.getVersion());
        unused(window);
        Times times = validateWindow(doctor, request);
        overlap(doctorId, windowId, request, times);
        window.update(request.serviceId(), request.date(), times.start(), times.end());
        return response(windows.saveAndFlush(window));
    }

    @Transactional
    public void deleteWindow(UUID doctorId, UUID windowId, String ifMatch) {
        lock(doctorId);
        DoctorBookingWindow window = lockWindow(doctorId, windowId);
        version(ifMatch, window.getVersion());
        unused(window);
        windows.delete(window);
        windows.flush();
    }

    private Doctor find(UUID id) { return doctors.findById(id).orElseThrow(AppointmentException::doctorNotFound); }
    private Doctor lock(UUID id) { return doctors.findForUpdate(id).orElseThrow(AppointmentException::doctorNotFound); }
    private DoctorBookingWindow lockWindow(UUID doctorId, UUID id) {
        return windows.lock(doctorId, id).orElseThrow(DoctorService::windowNotFound);
    }

    private void unused(DoctorBookingWindow window) {
        if (checks.inUse(window)) throw conflict("SCHEDULE_IN_USE", "This working window is used by an appointment and cannot be changed.");
    }

    private record Times(LocalTime start, LocalTime end) { }

    private Times validateWindow(Doctor doctor, BookingWindowDtos.Request request) {
        if (!doctor.isActive()) throw AppointmentException.doctorNotFound();
        if (request == null || request.serviceId() == null) throw AppointmentException.invalidField("serviceId", "Choose a service.");
        LocalDate today = LocalDate.now(ClinicTime.ZONE);
        if (request.date() == null || request.date().isBefore(today) || request.date().isAfter(today.plusDays(settings.windowDays()))) {
            throw AppointmentException.invalidField("date", "Choose a date within the configured booking window.");
        }
        LocalTime start = time(request.startTime(), "startTime");
        LocalTime end = time(request.endTime(), "endTime");
        var service = services.findById(request.serviceId()).filter(s -> s.getStatus() == ServiceStatus.ACTIVE)
                .orElseThrow(AppointmentException::serviceNotFound);
        if (!end.isAfter(start) || ChronoUnit.MINUTES.between(start, end) < service.getDurationMinutes() + service.getBufferMinutes()) {
            throw AppointmentException.invalidField("endTime", "The working window must fit the service duration and buffer after its start.");
        }
        return new Times(start, end);
    }

    private void overlap(UUID doctorId, UUID excludedId, BookingWindowDtos.Request request, Times times) {
        if (windows.overlaps(doctorId, request.serviceId(), request.date(), times.start(), times.end(), excludedId) > 0) {
            throw conflict("BOOKING_WINDOW_OVERLAP", "A working window already overlaps for this doctor, service and date.");
        }
    }

    private static LocalTime time(String value, String field) {
        if (value == null || !value.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]")) {
            throw AppointmentException.invalidField(field, "Use HH:mm in clinic time.");
        }
        return LocalTime.parse(value);
    }

    private static void validate(DoctorRequest request) {
        if (request == null || request.fullName() == null || request.fullName().strip().isEmpty() || request.fullName().strip().length() > 120) {
            throw AppointmentException.invalidField("fullName", "Enter a name of 1 to 120 characters.");
        }
        if (request.status() == null) throw AppointmentException.invalidField("status", "Choose ACTIVE or INACTIVE.");
        if (request.sortOrder() == null || request.sortOrder() < 0) throw AppointmentException.invalidField("sortOrder", "Use a non-negative integer.");
    }

    private static void version(String header, long actual) {
        if (header == null || header.isBlank()) throw AppointmentException.versionRequired();
        if (!header.strip().matches("\"[0-9]{1,18}\"") || Long.parseLong(header.strip().replace("\"", "")) != actual) {
            throw new AppointmentException(412, "VERSION_MISMATCH", "Reload the doctor or working window before changing it.");
        }
    }

    public static AppointmentException windowNotFound() {
        return new AppointmentException(404, "BOOKING_WINDOW_NOT_FOUND", "Doctor working window not found.");
    }

    private static AppointmentException conflict(String code, String detail) { return new AppointmentException(409, code, detail); }

    private static DoctorResponse response(Doctor doctor) {
        return new DoctorResponse(doctor.getId(), doctor.getFullName(), doctor.getStatus(), doctor.getSortOrder(), doctor.getVersion(),
                doctor.getCreatedAt().atZone(ClinicTime.ZONE).toOffsetDateTime(), doctor.getUpdatedAt().atZone(ClinicTime.ZONE).toOffsetDateTime());
    }

    private static BookingWindowDtos.Response response(DoctorBookingWindow window) {
        return new BookingWindowDtos.Response(window.getId(), window.getDoctorId(), window.getServiceId(), window.getDay(),
                window.getStartTime().toString(), window.getEndTime().toString(), window.getVersion());
    }
}
