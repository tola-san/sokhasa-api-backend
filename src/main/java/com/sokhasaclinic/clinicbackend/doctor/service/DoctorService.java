package com.sokhasaclinic.clinicbackend.doctor.service;

import com.sokhasaclinic.clinicbackend.appointment.repository.AppointmentRepository;
import com.sokhasaclinic.clinicbackend.common.dto.ApiResponsePage;
import com.sokhasaclinic.clinicbackend.common.exception.ResourceNotFoundException;
import com.sokhasaclinic.clinicbackend.doctor.dto.AvailabilityResponse;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorRequest;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorResponse;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorScheduleRequest;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorScheduleResponse;
import com.sokhasaclinic.clinicbackend.doctor.model.Doctor;
import com.sokhasaclinic.clinicbackend.doctor.model.DoctorSchedule;
import com.sokhasaclinic.clinicbackend.doctor.repository.DoctorRepository;
import com.sokhasaclinic.clinicbackend.doctor.repository.DoctorScheduleRepository;
import com.sokhasaclinic.clinicbackend.service.model.ClinicService;
import com.sokhasaclinic.clinicbackend.service.repository.ClinicServiceRepository;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final DoctorScheduleRepository scheduleRepository;
    private final ClinicServiceRepository clinicServiceRepository;
    private final AppointmentRepository appointmentRepository;

    public DoctorService(
            DoctorRepository doctorRepository,
            DoctorScheduleRepository scheduleRepository,
            ClinicServiceRepository clinicServiceRepository,
            AppointmentRepository appointmentRepository) {
        this.doctorRepository = doctorRepository;
        this.scheduleRepository = scheduleRepository;
        this.clinicServiceRepository = clinicServiceRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @Transactional(readOnly = true)
    public ApiResponsePage<DoctorResponse> listDoctors(Doctor.Status status, String search, Pageable pageable) {
        Page<Doctor> page = doctorRepository.findWithFilter(status, search, pageable);
        return ApiResponsePage.of(page.map(DoctorResponse::fromEntity));
    }

    @Transactional(readOnly = true)
    public List<DoctorResponse> listPublicDoctors() {
        return doctorRepository.findAllByStatus(Doctor.Status.ACTIVE)
                .stream()
                .map(DoctorResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public DoctorResponse getDoctorById(UUID id) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + id, "DOCTOR_NOT_FOUND"));
        return DoctorResponse.fromEntity(doctor);
    }

    @Transactional
    public DoctorResponse createDoctor(DoctorRequest request) {
        Doctor doctor = Doctor.builder()
                .name(request.name().trim())
                .profileImageId(request.profileImageId())
                .description(request.description())
                .status(request.status() != null ? request.status() : Doctor.Status.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        return DoctorResponse.fromEntity(doctorRepository.save(doctor));
    }

    @Transactional
    public DoctorResponse updateDoctor(UUID id, DoctorRequest request) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + id, "DOCTOR_NOT_FOUND"));
        doctor.setName(request.name().trim());
        doctor.setProfileImageId(request.profileImageId());
        doctor.setDescription(request.description());
        if (request.status() != null) {
            doctor.setStatus(request.status());
        }
        doctor.setUpdatedAt(Instant.now());
        return DoctorResponse.fromEntity(doctorRepository.save(doctor));
    }

    @Transactional
    public DoctorResponse setDoctorStatus(UUID id, Doctor.Status status) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + id, "DOCTOR_NOT_FOUND"));
        doctor.setStatus(status);
        doctor.setUpdatedAt(Instant.now());
        return DoctorResponse.fromEntity(doctorRepository.save(doctor));
    }

    @Transactional
    public void deleteDoctor(UUID id) {
        if (!doctorRepository.existsById(id)) {
            throw new ResourceNotFoundException("Doctor not found: " + id, "DOCTOR_NOT_FOUND");
        }
        doctorRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<DoctorScheduleResponse> listDoctorSchedules(UUID doctorId) {
        if (!doctorRepository.existsById(doctorId)) {
            throw new ResourceNotFoundException("Doctor not found: " + doctorId, "DOCTOR_NOT_FOUND");
        }
        return scheduleRepository.findAllByDoctorId(doctorId).stream()
                .map(DoctorScheduleResponse::fromEntity)
                .toList();
    }

    @Transactional
    public DoctorScheduleResponse createDoctorSchedule(UUID doctorId, DoctorScheduleRequest request) {
        if (!doctorRepository.existsById(doctorId)) {
            throw new ResourceNotFoundException("Doctor not found: " + doctorId, "DOCTOR_NOT_FOUND");
        }
        DoctorSchedule schedule = DoctorSchedule.builder()
                .doctorId(doctorId)
                .dayOfWeek(request.dayOfWeek())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .effectiveFrom(request.effectiveFrom())
                .effectiveUntil(request.effectiveUntil())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        return DoctorScheduleResponse.fromEntity(scheduleRepository.save(schedule));
    }

    @Transactional
    public DoctorScheduleResponse updateDoctorSchedule(UUID doctorId, UUID scheduleId, DoctorScheduleRequest request) {
        DoctorSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found: " + scheduleId, "SCHEDULE_NOT_FOUND"));
        if (!schedule.getDoctorId().equals(doctorId)) {
            throw new ResourceNotFoundException("Schedule does not belong to doctor: " + doctorId, "SCHEDULE_NOT_FOUND");
        }
        schedule.setDayOfWeek(request.dayOfWeek());
        schedule.setStartTime(request.startTime());
        schedule.setEndTime(request.endTime());
        schedule.setEffectiveFrom(request.effectiveFrom());
        schedule.setEffectiveUntil(request.effectiveUntil());
        schedule.setUpdatedAt(Instant.now());
        return DoctorScheduleResponse.fromEntity(scheduleRepository.save(schedule));
    }

    @Transactional
    public void deleteDoctorSchedule(UUID doctorId, UUID scheduleId) {
        DoctorSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found: " + scheduleId, "SCHEDULE_NOT_FOUND"));
        if (!schedule.getDoctorId().equals(doctorId)) {
            throw new ResourceNotFoundException("Schedule does not belong to doctor: " + doctorId, "SCHEDULE_NOT_FOUND");
        }
        scheduleRepository.delete(schedule);
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse checkAvailability(UUID serviceId, UUID doctorId, LocalDate date) {
        ClinicService service = clinicServiceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + serviceId, "SERVICE_NOT_FOUND"));

        int durationMinutes = service.getDurationMinutes();
        DayOfWeek dayOfWeek = date.getDayOfWeek();

        List<DoctorSchedule> activeSchedules;
        if (doctorId != null) {
            activeSchedules = scheduleRepository.findActiveSchedules(doctorId, dayOfWeek, date);
        } else {
            // If no specific doctor requested, consider any active doctor's schedule
            List<Doctor> activeDoctors = doctorRepository.findAllByStatus(Doctor.Status.ACTIVE);
            activeSchedules = new ArrayList<>();
            for (Doctor doc : activeDoctors) {
                activeSchedules.addAll(scheduleRepository.findActiveSchedules(doc.getId(), dayOfWeek, date));
            }
        }

        List<AvailabilityResponse.Slot> slots = new ArrayList<>();

        for (DoctorSchedule schedule : activeSchedules) {
            LocalTime slotStart = schedule.getStartTime();
            while (slotStart.plusMinutes(durationMinutes).compareTo(schedule.getEndTime()) <= 0) {
                LocalTime slotEnd = slotStart.plusMinutes(durationMinutes);

                boolean isOccupied = !appointmentRepository.findOverlappingDoctorAppointments(
                        schedule.getDoctorId(), date, slotStart, slotEnd, null
                ).isEmpty();

                LocalTime currentSlotStart = slotStart;
                boolean alreadyAdded = slots.stream().anyMatch(s -> s.startTime().equals(currentSlotStart));
                if (!alreadyAdded) {
                    slots.add(new AvailabilityResponse.Slot(slotStart, slotEnd, !isOccupied));
                }

                slotStart = slotEnd;
            }
        }

        // Sort slots by start time
        slots.sort((a, b) -> a.startTime().compareTo(b.startTime()));

        return new AvailabilityResponse(date, serviceId, doctorId, slots);
    }
}
