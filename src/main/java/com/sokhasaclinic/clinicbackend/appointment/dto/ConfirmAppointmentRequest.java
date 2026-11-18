package com.sokhasaclinic.clinicbackend.appointment.dto;

import java.util.UUID;

public record ConfirmAppointmentRequest(
        UUID doctorId,
        String notes
) {}
