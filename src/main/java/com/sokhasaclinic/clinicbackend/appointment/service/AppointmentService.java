package com.sokhasaclinic.clinicbackend.appointment.service;

import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentDetailResponse;
import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentStatusHistoryDto;
import com.sokhasaclinic.clinicbackend.appointment.dto.AppointmentSummaryResponse;
import com.sokhasaclinic.clinicbackend.appointment.dto.AssignDoctorRequest;
import com.sokhasaclinic.clinicbackend.appointment.dto.ConfirmAppointmentRequest;
import com.sokhasaclinic.clinicbackend.appointment.dto.CreateAppointmentRequest;
import com.sokhasaclinic.clinicbackend.appointment.dto.PublicAppointmentCreatedResponse;
import com.sokhasaclinic.clinicbackend.appointment.dto.RejectAppointmentRequest;
import com.sokhasaclinic.clinicbackend.appointment.dto.RescheduleAppointmentRequest;
import com.sokhasaclinic.clinicbackend.appointment.model.Appointment;
import com.sokhasaclinic.clinicbackend.appointment.model.AppointmentStatusHistory;
import com.sokhasaclinic.clinicbackend.appointment.repository.AppointmentRepository;
import com.sokhasaclinic.clinicbackend.appointment.repository.AppointmentStatusHistoryRepository;
import com.sokhasaclinic.clinicbackend.common.dto.ApiResponsePage;
import com.sokhasaclinic.clinicbackend.common.exception.ConflictException;
import com.sokhasaclinic.clinicbackend.common.exception.ResourceNotFoundException;
import com.sokhasaclinic.clinicbackend.common.exception.ValidationException;
import com.sokhasaclinic.clinicbackend.doctor.model.Doctor;
import com.sokhasaclinic.clinicbackend.doctor.repository.DoctorRepository;
import com.sokhasaclinic.clinicbackend.notification.service.NotificationOutboxService;
import com.sokhasaclinic.clinicbackend.patient.model.Patient;
import com.sokhasaclinic.clinicbackend.patient.service.PatientService;
import com.sokhasaclinic.clinicbackend.service.model.ClinicService;
import com.sokhasaclinic.clinicbackend.service.repository.ClinicServiceRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentStatusHistoryRepository statusHistoryRepository;
    private final PatientService patientService;
    private final ClinicServiceRepository clinicServiceRepository;
    private final DoctorRepository doctorRepository;
    private final NotificationOutboxService outboxService;
    private final SecureRandom random = new SecureRandom();

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            AppointmentStatusHistoryRepository statusHistoryRepository,
            PatientService patientService,
            ClinicServiceRepository clinicServiceRepository,
            DoctorRepository doctorRepository,
            NotificationOutboxService outboxService) {
        this.appointmentRepository = appointmentRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.patientService = patientService;
        this.clinicServiceRepository = clinicServiceRepository;
        this.doctorRepository = doctorRepository;
        this.outboxService = outboxService;
    }

    @Transactional
    public PublicAppointmentCreatedResponse createPublicBooking(CreateAppointmentRequest request) {
        ClinicService service = clinicServiceRepository.findById(request.serviceId())
                .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + request.serviceId(), "SERVICE_NOT_FOUND"));

        if (service.getStatus() != ClinicService.Status.ACTIVE) {
            throw new ValidationException("Selected service is inactive.", "SERVICE_INACTIVE");
        }

        if (request.preferredDoctorId() != null) {
            Doctor doctor = doctorRepository.findById(request.preferredDoctorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + request.preferredDoctorId(), "DOCTOR_NOT_FOUND"));
            if (doctor.getStatus() != Doctor.Status.ACTIVE) {
                throw new ValidationException("Preferred doctor is inactive.", "DOCTOR_INACTIVE");
            }
        }

        LocalTime endTime = request.startTime().plusMinutes(service.getDurationMinutes());
        Patient patient = patientService.findOrCreatePatient(request.patient());

        String reference = generateUniqueReference(request.appointmentDate());

        Appointment appointment = Appointment.builder()
                .reference(reference)
                .patient(patient)
                .service(service)
                .preferredDoctorId(request.preferredDoctorId())
                .appointmentDate(request.appointmentDate())
                .startTime(request.startTime())
                .endTime(endTime)
                .status(Appointment.Status.PENDING)
                .notes(request.notes())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Appointment saved = appointmentRepository.save(appointment);

        recordHistory(saved, null, Appointment.Status.PENDING.name(), null, "Patient", "Appointment request submitted.");

        outboxService.recordEvent(
                "APPOINTMENT_CREATED",
                saved.getId(),
                Map.of(
                        "appointmentId", saved.getId(),
                        "reference", saved.getReference(),
                        "patientName", patient.getFullName(),
                        "phone", patient.getPhone(),
                        "date", saved.getAppointmentDate().toString(),
                        "startTime", saved.getStartTime().toString()
                )
        );

        return new PublicAppointmentCreatedResponse(
                saved.getId(),
                saved.getReference(),
                saved.getAppointmentDate(),
                saved.getStartTime(),
                saved.getEndTime(),
                saved.getStatus(),
                "Appointment request submitted successfully."
        );
    }

    @Transactional(readOnly = true)
    public ApiResponsePage<AppointmentSummaryResponse> listAppointments(
            Appointment.Status status,
            LocalDate date,
            LocalDate dateFrom,
            LocalDate dateTo,
            UUID doctorId,
            UUID serviceId,
            String search,
            Pageable pageable) {

        Page<Appointment> page = appointmentRepository.findWithFilters(
                status, date, dateFrom, dateTo, doctorId, serviceId, search, pageable
        );
        return ApiResponsePage.of(page.map(AppointmentSummaryResponse::fromEntity));
    }

    @Transactional(readOnly = true)
    public AppointmentDetailResponse getAppointmentDetails(UUID id) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + id, "APPOINTMENT_NOT_FOUND"));

        List<AppointmentStatusHistoryDto> history = statusHistoryRepository
                .findAllByAppointmentIdOrderByCreatedAtDesc(id)
                .stream()
                .map(AppointmentStatusHistoryDto::fromEntity)
                .toList();

        return AppointmentDetailResponse.fromEntity(appointment, history);
    }

    @Transactional
    public AppointmentDetailResponse assignDoctor(UUID id, AssignDoctorRequest request, String staffUserId, String staffUsername) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + id, "APPOINTMENT_NOT_FOUND"));

        Doctor doctor = doctorRepository.findById(request.doctorId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + request.doctorId(), "DOCTOR_NOT_FOUND"));

        if (doctor.getStatus() != Doctor.Status.ACTIVE) {
            throw new ConflictException("Doctor is not active.", "DOCTOR_NOT_ACTIVE");
        }

        // Verify conflict
        List<Appointment> overlaps = appointmentRepository.findOverlappingDoctorAppointments(
                doctor.getId(), appointment.getAppointmentDate(), appointment.getStartTime(), appointment.getEndTime(), appointment.getId()
        );
        if (!overlaps.isEmpty()) {
            throw new ConflictException("Doctor has an overlapping appointment at the requested time.", "APPOINTMENT_CONFLICT");
        }

        appointment.setAssignedDoctor(doctor);
        appointment.setUpdatedAt(Instant.now());
        Appointment saved = appointmentRepository.save(appointment);

        recordHistory(saved, saved.getStatus().name(), saved.getStatus().name(), staffUserId, staffUsername,
                "Assigned doctor: " + doctor.getName() + (request.reason() != null ? " (" + request.reason() + ")" : ""));

        return getAppointmentDetails(saved.getId());
    }

    @Transactional
    public AppointmentDetailResponse confirmAppointment(UUID id, ConfirmAppointmentRequest request, String staffUserId, String staffUsername) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + id, "APPOINTMENT_NOT_FOUND"));

        if (appointment.getStatus() != Appointment.Status.PENDING) {
            throw new ConflictException("Only PENDING appointments can be confirmed. Current status: " + appointment.getStatus(), "INVALID_STATUS_TRANSITION");
        }

        UUID docId = request.doctorId() != null ? request.doctorId()
                : (appointment.getAssignedDoctor() != null ? appointment.getAssignedDoctor().getId() : appointment.getPreferredDoctorId());

        if (docId == null) {
            throw new ValidationException("A doctor must be assigned to confirm the appointment.", "DOCTOR_REQUIRED");
        }

        Doctor doctor = doctorRepository.findById(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + docId, "DOCTOR_NOT_FOUND"));

        if (doctor.getStatus() != Doctor.Status.ACTIVE) {
            throw new ConflictException("Assigned doctor is not active.", "DOCTOR_NOT_ACTIVE");
        }

        List<Appointment> overlaps = appointmentRepository.findOverlappingDoctorAppointments(
                doctor.getId(), appointment.getAppointmentDate(), appointment.getStartTime(), appointment.getEndTime(), appointment.getId()
        );
        if (!overlaps.isEmpty()) {
            throw new ConflictException("Doctor has a conflicting appointment at this time.", "APPOINTMENT_CONFLICT");
        }

        appointment.setAssignedDoctor(doctor);
        appointment.setStatus(Appointment.Status.CONFIRMED);
        appointment.setUpdatedAt(Instant.now());
        Appointment saved = appointmentRepository.save(appointment);

        recordHistory(saved, Appointment.Status.PENDING.name(), Appointment.Status.CONFIRMED.name(),
                staffUserId, staffUsername, request.notes() != null ? request.notes() : "Appointment confirmed.");

        outboxService.recordEvent(
                "APPOINTMENT_CONFIRMED",
                saved.getId(),
                Map.of(
                        "appointmentId", saved.getId(),
                        "reference", saved.getReference(),
                        "doctorName", doctor.getName(),
                        "date", saved.getAppointmentDate().toString(),
                        "startTime", saved.getStartTime().toString()
                )
        );

        return getAppointmentDetails(saved.getId());
    }

    @Transactional
    public AppointmentDetailResponse updateVisitStatus(UUID id, com.sokhasaclinic.clinicbackend.appointment.dto.UpdateAppointmentStatusRequest request, String staffUserId, String staffUsername) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + id, "APPOINTMENT_NOT_FOUND"));

        Appointment.Status current = appointment.getStatus();
        Appointment.Status target = request.status();

        boolean allowed = switch (current) {
            case CONFIRMED -> target == Appointment.Status.ARRIVED;
            case ARRIVED -> target == Appointment.Status.IN_PROGRESS;
            case IN_PROGRESS -> target == Appointment.Status.COMPLETED;
            default -> false;
        };

        if (!allowed) {
            throw new ConflictException(
                    String.format("Invalid visit transition from %s to %s.", current, target),
                    "INVALID_STATUS_TRANSITION"
            );
        }

        appointment.setStatus(target);
        appointment.setUpdatedAt(Instant.now());
        Appointment saved = appointmentRepository.save(appointment);

        recordHistory(saved, current.name(), target.name(), staffUserId, staffUsername,
                request.notes() != null ? request.notes() : "Visit status updated to " + target.name());

        return getAppointmentDetails(saved.getId());
    }

    @Transactional
    public AppointmentDetailResponse rejectAppointment(UUID id, RejectAppointmentRequest request, String staffUserId, String staffUsername) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + id, "APPOINTMENT_NOT_FOUND"));

        if (appointment.getStatus() != Appointment.Status.PENDING) {
            throw new ConflictException("Only PENDING appointments can be rejected. Current status: " + appointment.getStatus(), "INVALID_STATUS_TRANSITION");
        }

        Appointment.Status oldStatus = appointment.getStatus();
        appointment.setStatus(Appointment.Status.REJECTED);
        appointment.setRejectionReason(request.reason());
        appointment.setUpdatedAt(Instant.now());
        Appointment saved = appointmentRepository.save(appointment);

        recordHistory(saved, oldStatus.name(), Appointment.Status.REJECTED.name(),
                staffUserId, staffUsername, request.reason());

        outboxService.recordEvent(
                "APPOINTMENT_REJECTED",
                saved.getId(),
                Map.of(
                        "appointmentId", saved.getId(),
                        "reference", saved.getReference(),
                        "reason", request.reason()
                )
        );

        return getAppointmentDetails(saved.getId());
    }

    @Transactional
    public AppointmentDetailResponse rescheduleAppointment(UUID id, RescheduleAppointmentRequest request, String staffUserId, String staffUsername) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + id, "APPOINTMENT_NOT_FOUND"));

        UUID docId = request.doctorId() != null ? request.doctorId()
                : (appointment.getAssignedDoctor() != null ? appointment.getAssignedDoctor().getId() : null);

        int duration = appointment.getService().getDurationMinutes();
        LocalTime newEndTime = request.startTime().plusMinutes(duration);

        if (docId != null) {
            Doctor doctor = doctorRepository.findById(docId)
                    .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + docId, "DOCTOR_NOT_FOUND"));
            if (doctor.getStatus() != Doctor.Status.ACTIVE) {
                throw new ConflictException("Doctor is not active.", "DOCTOR_NOT_ACTIVE");
            }
            List<Appointment> overlaps = appointmentRepository.findOverlappingDoctorAppointments(
                    docId, request.appointmentDate(), request.startTime(), newEndTime, appointment.getId()
            );
            if (!overlaps.isEmpty()) {
                throw new ConflictException("Doctor has a conflicting appointment at the requested rescheduled time.", "APPOINTMENT_CONFLICT");
            }
            appointment.setAssignedDoctor(doctor);
        }

        LocalDate oldDate = appointment.getAppointmentDate();
        LocalTime oldStart = appointment.getStartTime();

        appointment.setAppointmentDate(request.appointmentDate());
        appointment.setStartTime(request.startTime());
        appointment.setEndTime(newEndTime);
        appointment.setUpdatedAt(Instant.now());
        Appointment saved = appointmentRepository.save(appointment);

        String changeDesc = String.format("Rescheduled from %s %s to %s %s. Reason: %s",
                oldDate, oldStart, request.appointmentDate(), request.startTime(),
                request.reason() != null ? request.reason() : "N/A");

        recordHistory(saved, saved.getStatus().name(), saved.getStatus().name(),
                staffUserId, staffUsername, changeDesc);

        outboxService.recordEvent(
                "APPOINTMENT_RESCHEDULED",
                saved.getId(),
                Map.of(
                        "appointmentId", saved.getId(),
                        "reference", saved.getReference(),
                        "newDate", request.appointmentDate().toString(),
                        "newStartTime", request.startTime().toString()
                )
        );

        return getAppointmentDetails(saved.getId());
    }

    @Transactional(readOnly = true)
    public List<AppointmentStatusHistoryDto> getAppointmentHistory(UUID id) {
        if (!appointmentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Appointment not found: " + id, "APPOINTMENT_NOT_FOUND");
        }
        return statusHistoryRepository.findAllByAppointmentIdOrderByCreatedAtDesc(id)
                .stream()
                .map(AppointmentStatusHistoryDto::fromEntity)
                .toList();
    }

    private void recordHistory(Appointment appointment, String oldStatus, String newStatus, String userId, String username, String reason) {
        AppointmentStatusHistory history = AppointmentStatusHistory.builder()
                .appointment(appointment)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .changedByUserId(userId)
                .changedByUsername(username)
                .reason(reason)
                .createdAt(Instant.now())
                .build();
        statusHistoryRepository.save(history);
    }

    private String generateUniqueReference(LocalDate date) {
        String datePrefix = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        for (int i = 0; i < 10; i++) {
            int seq = 1000 + random.nextInt(9000);
            String ref = String.format("SK-%s-%04d", datePrefix, seq);
            if (appointmentRepository.findByReference(ref).isEmpty()) {
                return ref;
            }
        }
        return "SK-" + datePrefix + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }
}
