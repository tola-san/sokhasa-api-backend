package com.sokhasaclinic.clinicbackend.appointment.dto;

import com.sokhasaclinic.clinicbackend.appointment.model.Appointment;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorResponse;
import com.sokhasaclinic.clinicbackend.patient.dto.PatientResponse;
import com.sokhasaclinic.clinicbackend.service.dto.ServiceResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record AppointmentDetailResponse(
        UUID id,
        String reference,
        PatientResponse patient,
        ServiceResponse service,
        UUID preferredDoctorId,
        DoctorResponse assignedDoctor,
        LocalDate appointmentDate,
        LocalTime startTime,
        LocalTime endTime,
        Appointment.Status status,
        String notes,
        String rejectionReason,
        List<AppointmentStatusHistoryDto> statusHistory,
        Instant createdAt,
        Instant updatedAt
) {
    public static AppointmentDetailResponse fromEntity(Appointment a, List<AppointmentStatusHistoryDto> history) {
        if (a == null) return null;
        return new AppointmentDetailResponse(
                a.getId(),
                a.getReference(),
                PatientResponse.fromEntity(a.getPatient()),
                ServiceResponse.fromEntity(a.getService()),
                a.getPreferredDoctorId(),
                DoctorResponse.fromEntity(a.getAssignedDoctor()),
                a.getAppointmentDate(),
                a.getStartTime(),
                a.getEndTime(),
                a.getStatus(),
                a.getNotes(),
                a.getRejectionReason(),
                history != null ? history : List.of(),
                a.getCreatedAt(),
                a.getUpdatedAt()
        );
    }
}
