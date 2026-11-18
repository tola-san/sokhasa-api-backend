package com.sokhasaclinic.clinicbackend.appointment.model;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Appointment lifecycle. This enum is the only place the allowed transitions are defined:
 * {@code PENDING → CONFIRMED | REJECTED} and {@code CONFIRMED → ARRIVED → IN_PROGRESS → COMPLETED}.
 * Adding a status or transition needs Team Lead approval.
 */
public enum AppointmentStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    ARRIVED,
    IN_PROGRESS,
    COMPLETED;

    private static final Map<AppointmentStatus, Set<AppointmentStatus>> NEXT = Map.of(
            PENDING, EnumSet.of(CONFIRMED, REJECTED),
            CONFIRMED, EnumSet.of(ARRIVED),
            ARRIVED, EnumSet.of(IN_PROGRESS),
            IN_PROGRESS, EnumSet.of(COMPLETED));

    public boolean canMoveTo(AppointmentStatus next) {
        return NEXT.getOrDefault(this, Set.of()).contains(next);
    }
}
