package com.sokhasaclinic.clinicbackend.appointment.dto;

import com.sokhasaclinic.clinicbackend.appointment.model.Appointment;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record PublicAppointmentCreatedResponse(
        UUID id,
        String reference,
        LocalDate appointmentDate,
        LocalTime startTime,
        LocalTime endTime,
        Appointment.Status status,
        String message
) {}
