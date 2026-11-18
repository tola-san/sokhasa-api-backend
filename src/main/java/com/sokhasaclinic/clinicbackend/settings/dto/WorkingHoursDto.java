package com.sokhasaclinic.clinicbackend.settings.dto;

import com.sokhasaclinic.clinicbackend.settings.model.ClinicWorkingHours;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;

public record WorkingHoursDto(
        @NotNull DayOfWeek dayOfWeek,
        @NotNull LocalTime openTime,
        @NotNull LocalTime closeTime,
        boolean isClosed
) {
    public static WorkingHoursDto fromEntity(ClinicWorkingHours hours) {
        if (hours == null) return null;
        return new WorkingHoursDto(
                hours.getDayOfWeek(),
                hours.getOpenTime(),
                hours.getCloseTime(),
                hours.isClosed()
        );
    }
}
