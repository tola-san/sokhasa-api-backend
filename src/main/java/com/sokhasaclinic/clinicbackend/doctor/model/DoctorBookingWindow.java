package com.sokhasaclinic.clinicbackend.doctor.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import lombok.Getter;

@Entity
@Table(name = "doctor_booking_window", schema = "clinic")
@Getter
public class DoctorBookingWindow {
    @Id private UUID id;
    @Column(name = "doctor_id", nullable = false, updatable = false) private UUID doctorId;
    @Column(name = "service_id", nullable = false) private UUID serviceId;
    @Column(nullable = false) private LocalDate day;
    @Column(name = "start_time", nullable = false) private LocalTime startTime;
    @Column(name = "end_time", nullable = false) private LocalTime endTime;
    @Version @Column(nullable = false) private long version;

    protected DoctorBookingWindow() { }

    public static DoctorBookingWindow create(UUID doctorId, UUID serviceId, LocalDate day,
            LocalTime start, LocalTime end) {
        var window = new DoctorBookingWindow();
        window.id = UUID.randomUUID();
        window.doctorId = doctorId;
        window.update(serviceId, day, start, end);
        return window;
    }

    public void update(UUID serviceId, LocalDate day, LocalTime start, LocalTime end) {
        this.serviceId = serviceId;
        this.day = day;
        this.startTime = start;
        this.endTime = end;
    }
}
