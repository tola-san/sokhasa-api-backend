package com.sokhasaclinic.clinicbackend.cms.repository;

import com.sokhasaclinic.clinicbackend.cms.model.CmsMedia;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for CMS Media.
 */
@Repository
public interface MediaRepository extends JpaRepository<CmsMedia, UUID> {
}
