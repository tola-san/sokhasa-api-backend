package com.sokhasaclinic.clinicbackend.appointment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import lombok.Getter;

/**
 * A time of day the patient can ask for, such as {@code MORNING} (decision BK-04). Times are local
 * clinic time. {@code capacityMinutes} is the total appointment time, buffers included, the clinic
 * accepts in the period on one date.
 */
@Entity
@Table(name = "booking_period", schema = "clinic")
@Getter
public class BookingPeriod {

    @Id
    @Column(nullable = false, updatable = false, length = 20)
    private String code;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "capacity_minutes", nullable = false)
    private int capacityMinutes;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected BookingPeriod() {
    }

    BookingPeriod(String code, LocalTime startTime, LocalTime endTime, int capacityMinutes) {
        this.code = code;
        this.startTime = startTime;
        this.endTime = endTime;
        this.capacityMinutes = capacityMinutes;
    }

    /**
     * Whether an appointment starting at {@code start} and lasting {@code totalMinutes} (duration
     * plus buffer) fits entirely inside this period.
     */
    public boolean contains(LocalTime start, int totalMinutes) {
        if (start.isBefore(startTime)) {
            return false;
        }
        // Compare in minutes so an appointment that would run past midnight cannot wrap around.
        int startMinute = start.toSecondOfDay() / 60;
        int endMinute = endTime.toSecondOfDay() / 60;
        return start.getSecond() == 0 && start.getNano() == 0 && startMinute + totalMinutes <= endMinute;
    }
}
