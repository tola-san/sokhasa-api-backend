package com.sokhasaclinic.clinicbackend.cms.repository;

import com.sokhasaclinic.clinicbackend.cms.model.HomepageBannerTranslation;
import org.springframework.data.jpa.repository.JpaRepository;

/** Keyed by language code ({@code km}, {@code en}, {@code zh}). */
public interface HomepageBannerTranslationRepository extends JpaRepository<HomepageBannerTranslation, String> {
}
