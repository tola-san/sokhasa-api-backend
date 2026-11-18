package com.sokhasaclinic.clinicbackend.cms.repository;

import com.sokhasaclinic.clinicbackend.cms.model.Page;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PageRepository extends JpaRepository<Page, UUID> {

    Optional<Page> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query("SELECT DISTINCT p FROM Page p LEFT JOIN FETCH p.translations WHERE p.id = :id")
    Optional<Page> findByIdWithTranslations(@Param("id") UUID id);

    @Query("SELECT DISTINCT p FROM Page p LEFT JOIN FETCH p.translations WHERE p.slug = :slug")
    Optional<Page> findBySlugWithTranslations(@Param("slug") String slug);

    @Query("SELECT DISTINCT p FROM Page p LEFT JOIN p.translations t WHERE " +
           "(:status IS NULL OR p.status = :status) AND " +
           "(:search IS NULL OR LOWER(p.slug) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%')))")
    org.springframework.data.domain.Page<Page> findWithFilter(
            @Param("status") Page.Status status,
            @Param("search") String search,
            Pageable pageable
    );
}
