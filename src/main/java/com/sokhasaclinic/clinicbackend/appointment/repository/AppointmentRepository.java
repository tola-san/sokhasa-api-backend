package com.sokhasaclinic.clinicbackend.appointment.repository;

import com.sokhasaclinic.clinicbackend.appointment.model.Appointment;
import com.sokhasaclinic.clinicbackend.appointment.model.AppointmentStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Appointment persistence, including the capacity count used under the period lock. */
public interface AppointmentRepository extends JpaRepository<Appointment, UUID>, JpaSpecificationExecutor<Appointment> {

    /**
     * Minutes (duration plus buffer) already taken in a period on a date. A {@code PENDING} request
     * counts against its preferred period, a confirmed or later appointment against the period that
     * contains its start (AP-10), and a {@code REJECTED} one not at all.
     */
    @Query("""
            select coalesce(sum(a.durationMinutes + a.bufferMinutes), 0)
            from Appointment a
            where (a.status = com.sokhasaclinic.clinicbackend.appointment.model.AppointmentStatus.PENDING
                    and a.preferredDate = :date and a.preferredPeriod = :period)
                or (a.status not in (
                        com.sokhasaclinic.clinicbackend.appointment.model.AppointmentStatus.PENDING,
                        com.sokhasaclinic.clinicbackend.appointment.model.AppointmentStatus.REJECTED)
                    and a.confirmedDate = :date and a.confirmedPeriod = :period)
            """)
    long minutesUsed(@Param("date") LocalDate date, @Param("period") String period);

    /**
     * Locks the row for a status change, so the version check and the update see the same row and
     * two receptionists acting at once are serialised: the second then fails the version check.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.id = :id")
    Optional<Appointment> findForUpdate(@Param("id") UUID id);

    /** The list, with the doctor loaded in the same query. */
    @Override
    @EntityGraph(attributePaths = "doctor")
    Page<Appointment> findAll(Specification<Appointment> specification, Pageable pageable);

    @EntityGraph(attributePaths = "doctor")
    @Query("select a from Appointment a where a.id = :id")
    Optional<Appointment> findWithDoctor(@Param("id") UUID id);

    long countByStatus(AppointmentStatus status);

    /** Confirmed and later appointments on a clinic date, counted per status. */
    @Query("""
            select a.status, count(a) from Appointment a
            where a.confirmedDate = :day
            group by a.status
            """)
    List<Object[]> countByStatusOn(@Param("day") LocalDate day);
}
