package com.sokhasaclinic.clinicbackend.settings.repository;

import com.sokhasaclinic.clinicbackend.settings.model.ClinicWorkingHours;
import java.time.DayOfWeek;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClinicWorkingHoursRepository extends JpaRepository<ClinicWorkingHours, UUID> {
    Optional<ClinicWorkingHours> findByDayOfWeek(DayOfWeek dayOfWeek);
}
