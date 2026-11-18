package com.sokhasaclinic.clinicbackend.doctor.dto;

import com.sokhasaclinic.clinicbackend.doctor.model.DoctorSchedule;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record DoctorScheduleResponse(
        UUID id,
        UUID doctorId,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        LocalDate effectiveFrom,
        LocalDate effectiveUntil,
        Instant createdAt,
        Instant updatedAt
) {
    public static DoctorScheduleResponse fromEntity(DoctorSchedule schedule) {
        if (schedule == null) return null;
        return new DoctorScheduleResponse(
                schedule.getId(),
                schedule.getDoctorId(),
                schedule.getDayOfWeek(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getEffectiveFrom(),
                schedule.getEffectiveUntil(),
                schedule.getCreatedAt(),
                schedule.getUpdatedAt()
        );
    }
}
