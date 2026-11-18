package com.sokhasaclinic.clinicbackend.appointment.repository;

import com.sokhasaclinic.clinicbackend.appointment.model.Appointment;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    Optional<Appointment> findByReference(String reference);

    @Query("SELECT a FROM Appointment a WHERE a.assignedDoctor.id = :doctorId " +
           "AND a.appointmentDate = :date " +
           "AND a.status IN ('CONFIRMED', 'ARRIVED', 'IN_PROGRESS') " +
           "AND (:excludeId IS NULL OR a.id <> :excludeId) " +
           "AND (a.startTime < :endTime AND a.endTime > :startTime)")
    List<Appointment> findOverlappingDoctorAppointments(
            @Param("doctorId") UUID doctorId,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("excludeId") UUID excludeId
    );

    @Query("SELECT a FROM Appointment a WHERE " +
           "(:status IS NULL OR a.status = :status) AND " +
           "(:date IS NULL OR a.appointmentDate = :date) AND " +
           "(:dateFrom IS NULL OR a.appointmentDate >= :dateFrom) AND " +
           "(:dateTo IS NULL OR a.appointmentDate <= :dateTo) AND " +
           "(:doctorId IS NULL OR a.assignedDoctor.id = :doctorId OR a.preferredDoctorId = :doctorId) AND " +
           "(:serviceId IS NULL OR a.service.id = :serviceId) AND " +
           "(:search IS NULL OR LOWER(a.reference) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "   OR LOWER(a.patient.fullName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "   OR LOWER(a.patient.phone) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Appointment> findWithFilters(
            @Param("status") Appointment.Status status,
            @Param("date") LocalDate date,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("doctorId") UUID doctorId,
            @Param("serviceId") UUID serviceId,
            @Param("search") String search,
            Pageable pageable
    );

    long countByAppointmentDate(LocalDate date);

    long countByStatus(Appointment.Status status);

    @Query("SELECT a FROM Appointment a WHERE a.appointmentDate >= :date " +
           "AND a.status = 'CONFIRMED' ORDER BY a.appointmentDate ASC, a.startTime ASC")
    List<Appointment> findUpcomingConfirmed(@Param("date") LocalDate date, Pageable pageable);
}
