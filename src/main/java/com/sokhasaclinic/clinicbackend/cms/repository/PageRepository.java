package com.sokhasaclinic.clinicbackend.cms.repository;

import com.sokhasaclinic.clinicbackend.cms.model.CmsPage;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for CMS Pages with pessimistic concurrency locking.
 */
@Repository
public interface PageRepository extends JpaRepository<CmsPage, UUID>, JpaSpecificationExecutor<CmsPage> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CmsPage p where p.id = :id")
    Optional<CmsPage> lock(@Param("id") UUID id);

    Optional<CmsPage> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
