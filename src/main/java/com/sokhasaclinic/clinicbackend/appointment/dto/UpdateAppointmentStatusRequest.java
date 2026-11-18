package com.sokhasaclinic.clinicbackend.appointment.dto;

import com.sokhasaclinic.clinicbackend.appointment.model.Appointment;
import jakarta.validation.constraints.NotNull;

public record UpdateAppointmentStatusRequest(
        @NotNull(message = "New appointment status is required.")
        Appointment.Status status,

        String notes
) {}
