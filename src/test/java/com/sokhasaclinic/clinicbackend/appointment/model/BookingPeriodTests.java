package com.sokhasaclinic.clinicbackend.appointment.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class BookingPeriodTests {

    private final BookingPeriod morning = new BookingPeriod("MORNING", LocalTime.of(8, 0), LocalTime.of(12, 0), 480);

    @Test
    void appointmentEndingExactlyAtCloseFits() {
        assertTrue(morning.contains(LocalTime.of(11, 20), 40));
        assertTrue(morning.contains(LocalTime.of(8, 0), 240));
    }

    @Test
    void appointmentRunningPastCloseDoesNotFit() {
        // Acceptance case: 11:40 for 30 + 10 minutes ends at 12:20.
        assertFalse(morning.contains(LocalTime.of(11, 40), 40));
    }

    @Test
    void appointmentStartingBeforeOpeningDoesNotFit() {
        assertFalse(morning.contains(LocalTime.of(7, 59), 30));
    }

    @Test
    void startWithSecondsIsRejected() {
        assertFalse(morning.contains(LocalTime.of(9, 0, 30), 30));
    }
}
