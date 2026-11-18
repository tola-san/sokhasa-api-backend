package com.sokhasaclinic.clinicbackend.appointment.service;

import com.sokhasaclinic.clinicbackend.appointment.model.ActorType;
import com.sokhasaclinic.clinicbackend.appointment.model.AppointmentStatus;
import com.sokhasaclinic.clinicbackend.appointment.model.AppointmentStatusHistory;
import java.time.Instant;

/** One entry of an appointment's status history: who, when, from, to and why. */
public record StatusChange(
        AppointmentStatus fromStatus,
        AppointmentStatus toStatus,
        ActorType actorType,
        String actorId,
        String actorName,
        String reason,
        Instant changedAt) {

    static StatusChange of(AppointmentStatusHistory row) {
        return new StatusChange(row.getFromStatus(), row.getToStatus(), row.getActorType(), row.getActorId(),
                row.getActorName(), row.getReason(), row.getChangedAt());
    }
}
