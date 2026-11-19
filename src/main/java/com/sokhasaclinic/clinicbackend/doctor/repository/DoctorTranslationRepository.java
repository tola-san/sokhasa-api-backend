package com.sokhasaclinic.clinicbackend.doctor.repository;

import com.sokhasaclinic.clinicbackend.clinicservice.model.TranslationId;
import com.sokhasaclinic.clinicbackend.doctor.model.DoctorTranslation;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DoctorTranslationRepository extends JpaRepository<DoctorTranslation, TranslationId> {

    @Query("select t from DoctorTranslation t where t.id.ownerId in :doctorIds")
    List<DoctorTranslation> findByDoctorIds(@Param("doctorIds") Collection<UUID> doctorIds);
}
