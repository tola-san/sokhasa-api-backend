package com.sokhasaclinic.clinicbackend.clinicservice.repository;

import com.sokhasaclinic.clinicbackend.clinicservice.model.ClinicServiceTranslation;
import com.sokhasaclinic.clinicbackend.clinicservice.model.TranslationId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Service text, one row per service and language. */
public interface ClinicServiceTranslationRepository extends JpaRepository<ClinicServiceTranslation, TranslationId> {

    @Query("select t from ClinicServiceTranslation t where t.id.ownerId in :serviceIds")
    List<ClinicServiceTranslation> findByServiceIds(@Param("serviceIds") Collection<UUID> serviceIds);
}
