package com.sokhasaclinic.clinicbackend.appointment.dto;

import com.sokhasaclinic.clinicbackend.appointment.model.Appointment;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AppointmentSummaryResponse(
        UUID id,
        String reference,
        PatientInfo patient,
        ServiceInfo service,
        DoctorInfo preferredDoctor,
        DoctorInfo assignedDoctor,
        LocalDate appointmentDate,
        LocalTime startTime,
        LocalTime endTime,
        Appointment.Status status
) {
    public record PatientInfo(String fullName, String phone) {}
    public record ServiceInfo(UUID id, String name) {}
    public record DoctorInfo(UUID id, String name) {}

    public static AppointmentSummaryResponse fromEntity(Appointment a) {
        if (a == null) return null;

        PatientInfo patient = a.getPatient() != null
                ? new PatientInfo(a.getPatient().getFullName(), a.getPatient().getPhone())
                : null;

        String serviceName = "Consultation";
        if (a.getService() != null && a.getService().getTranslations() != null && !a.getService().getTranslations().isEmpty()) {
            serviceName = a.getService().getTranslations().get(0).getName();
        }
        ServiceInfo service = a.getService() != null ? new ServiceInfo(a.getService().getId(), serviceName) : null;

        DoctorInfo preferred = null;
        DoctorInfo assigned = a.getAssignedDoctor() != null
                ? new DoctorInfo(a.getAssignedDoctor().getId(), a.getAssignedDoctor().getName())
                : null;

        return new AppointmentSummaryResponse(
                a.getId(),
                a.getReference(),
                patient,
                service,
                preferred,
                assigned,
                a.getAppointmentDate(),
                a.getStartTime(),
                a.getEndTime(),
                a.getStatus()
        );
    }
}
