package com.sokhasaclinic.clinicbackend.doctor.repository;

import com.sokhasaclinic.clinicbackend.doctor.model.Doctor;
import com.sokhasaclinic.clinicbackend.doctor.model.DoctorStatus;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

/** Doctor resources; management writes serialize with booking through row locks. */
public interface DoctorRepository extends JpaRepository<Doctor, UUID> {

    /** AP-16: doctors reception can assign, in display order. */
    List<Doctor> findByStatusOrderBySortOrderAscFullNameAsc(DoctorStatus status);
    List<Doctor> findAllByOrderBySortOrderAscFullNameAscIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Doctor d where d.id = :id")
    Optional<Doctor> findForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select d from Doctor d where d.id = :id and d.status = com.sokhasaclinic.clinicbackend.doctor.model.DoctorStatus.ACTIVE")
    Optional<Doctor> findActiveForShare(@Param("id") UUID id);
}
