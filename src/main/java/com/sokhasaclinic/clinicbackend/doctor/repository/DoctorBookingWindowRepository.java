package com.sokhasaclinic.clinicbackend.doctor.repository;

import com.sokhasaclinic.clinicbackend.doctor.model.DoctorBookingWindow;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DoctorBookingWindowRepository extends JpaRepository<DoctorBookingWindow, UUID> {
    @Query("""
            select w from DoctorBookingWindow w where w.doctorId = :doctorId
            and w.day between :from and :to and (:serviceId is null or w.serviceId = :serviceId)
            order by w.day, w.startTime, w.id
            """)
    List<DoctorBookingWindow> list(@Param("doctorId") UUID doctorId, @Param("from") LocalDate from,
            @Param("to") LocalDate to, @Param("serviceId") UUID serviceId);

    Optional<DoctorBookingWindow> findByIdAndDoctorId(UUID id, UUID doctorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from DoctorBookingWindow w where w.id = :id and w.doctorId = :doctorId")
    Optional<DoctorBookingWindow> lock(@Param("doctorId") UUID doctorId, @Param("id") UUID id);

    @Query("""
            select count(w) from DoctorBookingWindow w where w.doctorId = :doctorId
            and w.serviceId = :serviceId and w.day = :day and w.id <> :excludedId
            and w.startTime < :end and w.endTime > :start
            """)
    long overlaps(@Param("doctorId") UUID doctorId, @Param("serviceId") UUID serviceId,
            @Param("day") LocalDate day, @Param("start") LocalTime start, @Param("end") LocalTime end,
            @Param("excludedId") UUID excludedId);
}
