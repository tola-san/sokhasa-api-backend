package com.sokhasaclinic.clinicbackend.doctor.repository;

import com.sokhasaclinic.clinicbackend.doctor.model.DoctorSchedule;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DoctorScheduleRepository extends JpaRepository<DoctorSchedule, UUID> {

    List<DoctorSchedule> findAllByDoctorId(UUID doctorId);

    @Query("SELECT s FROM DoctorSchedule s WHERE s.doctorId = :doctorId " +
           "AND s.dayOfWeek = :dayOfWeek " +
           "AND s.effectiveFrom <= :date " +
           "AND (s.effectiveUntil IS NULL OR s.effectiveUntil >= :date)")
    List<DoctorSchedule> findActiveSchedules(
            @Param("doctorId") UUID doctorId,
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("date") LocalDate date
    );
}
