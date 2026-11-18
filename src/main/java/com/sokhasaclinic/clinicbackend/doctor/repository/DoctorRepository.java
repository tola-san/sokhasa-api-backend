package com.sokhasaclinic.clinicbackend.doctor.repository;

import com.sokhasaclinic.clinicbackend.doctor.model.Doctor;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, UUID> {

    List<Doctor> findAllByStatus(Doctor.Status status);

    @Query("SELECT d FROM Doctor d WHERE " +
           "(:status IS NULL OR d.status = :status) AND " +
           "(:search IS NULL OR LOWER(d.name) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Doctor> findWithFilter(
            @Param("status") Doctor.Status status,
            @Param("search") String search,
            Pageable pageable
    );
}
