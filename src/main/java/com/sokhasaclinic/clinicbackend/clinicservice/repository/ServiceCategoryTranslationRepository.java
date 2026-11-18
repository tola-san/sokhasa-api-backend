package com.sokhasaclinic.clinicbackend.clinicservice.repository;

import com.sokhasaclinic.clinicbackend.clinicservice.model.ServiceCategoryTranslation;
import com.sokhasaclinic.clinicbackend.clinicservice.model.TranslationId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Category names, one row per category and language. Read-only in phase 1. */
public interface ServiceCategoryTranslationRepository
        extends JpaRepository<ServiceCategoryTranslation, TranslationId> {

    @Query("""
            select t from ServiceCategoryTranslation t
            where t.id.ownerId in :categoryIds and t.id.languageCode = :languageCode
            """)
    List<ServiceCategoryTranslation> findNames(
            @Param("categoryIds") Collection<UUID> categoryIds,
            @Param("languageCode") String languageCode);
}
