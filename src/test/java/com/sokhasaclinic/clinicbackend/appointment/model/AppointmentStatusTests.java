package com.sokhasaclinic.clinicbackend.appointment.model;

import static com.sokhasaclinic.clinicbackend.appointment.model.AppointmentStatus.ARRIVED;
import static com.sokhasaclinic.clinicbackend.appointment.model.AppointmentStatus.COMPLETED;
import static com.sokhasaclinic.clinicbackend.appointment.model.AppointmentStatus.CONFIRMED;
import static com.sokhasaclinic.clinicbackend.appointment.model.AppointmentStatus.IN_PROGRESS;
import static com.sokhasaclinic.clinicbackend.appointment.model.AppointmentStatus.PENDING;
import static com.sokhasaclinic.clinicbackend.appointment.model.AppointmentStatus.REJECTED;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The approved lifecycle and nothing else: every pair of statuses is checked. */
class AppointmentStatusTests {

    private static final Map<AppointmentStatus, Set<AppointmentStatus>> ALLOWED = Map.of(
            PENDING, EnumSet.of(CONFIRMED, REJECTED),
            CONFIRMED, EnumSet.of(ARRIVED),
            ARRIVED, EnumSet.of(IN_PROGRESS),
            IN_PROGRESS, EnumSet.of(COMPLETED),
            REJECTED, EnumSet.noneOf(AppointmentStatus.class),
            COMPLETED, EnumSet.noneOf(AppointmentStatus.class));

    @Test
    void onlyTheApprovedTransitionsAreAllowed() {
        for (AppointmentStatus from : AppointmentStatus.values()) {
            for (AppointmentStatus to : AppointmentStatus.values()) {
                assertEquals(ALLOWED.get(from).contains(to), from.canMoveTo(to), from + " -> " + to);
            }
        }
    }
}
