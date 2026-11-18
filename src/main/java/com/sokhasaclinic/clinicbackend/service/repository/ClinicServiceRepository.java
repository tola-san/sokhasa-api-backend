package com.sokhasaclinic.clinicbackend.service.repository;

import com.sokhasaclinic.clinicbackend.service.model.ClinicService;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ClinicServiceRepository extends JpaRepository<ClinicService, UUID> {

    @Query("SELECT DISTINCT s FROM ClinicService s LEFT JOIN FETCH s.translations WHERE s.status = :status")
    List<ClinicService> findAllByStatusWithTranslations(@Param("status") ClinicService.Status status);

    @Query("SELECT DISTINCT s FROM ClinicService s LEFT JOIN FETCH s.translations WHERE s.id = :id")
    ClinicService findByIdWithTranslations(@Param("id") UUID id);

    @Query("SELECT s FROM ClinicService s WHERE (:status IS NULL OR s.status = :status)")
    Page<ClinicService> findWithFilter(@Param("status") ClinicService.Status status, Pageable pageable);
}
