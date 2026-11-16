package com.sokhasaclinic.clinicbackend.appointment.service;

/**
 * Start every booking in PENDING. Support receptionist approval or rejection and the
 * CONFIRMED, ARRIVED, IN_PROGRESS, and COMPLETED visit sequence. Check schedule conflicts and
 * persist changes within the same transaction.
 */
public class AppointmentService {
    // TODO: Inject the repository and add transaction boundaries around writes.
}
