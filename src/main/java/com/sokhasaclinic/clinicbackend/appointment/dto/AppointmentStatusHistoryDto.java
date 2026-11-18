package com.sokhasaclinic.clinicbackend.appointment.dto;

import com.sokhasaclinic.clinicbackend.appointment.model.AppointmentStatusHistory;
import java.time.Instant;
import java.util.UUID;

public record AppointmentStatusHistoryDto(
        UUID id,
        String oldStatus,
        String newStatus,
        ChangedBy changedBy,
        String reason,
        Instant createdAt
) {
    public record ChangedBy(String id, String username) {}

    public static AppointmentStatusHistoryDto fromEntity(AppointmentStatusHistory h) {
        if (h == null) return null;
        ChangedBy cb = h.getChangedByUsername() != null || h.getChangedByUserId() != null
                ? new ChangedBy(h.getChangedByUserId(), h.getChangedByUsername())
                : null;
        return new AppointmentStatusHistoryDto(
                h.getId(),
                h.getOldStatus(),
                h.getNewStatus(),
                cb,
                h.getReason(),
                h.getCreatedAt()
        );
    }
}
