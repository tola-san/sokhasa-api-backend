package com.sokhasaclinic.clinicbackend.clinicservice.repository;

import com.sokhasaclinic.clinicbackend.clinicservice.model.ClinicService;
import com.sokhasaclinic.clinicbackend.clinicservice.model.ServiceStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Read access to clinic services. Writes come with the service editor, after the MVP. */
public interface ClinicServiceRepository extends JpaRepository<ClinicService, UUID> {

    /**
     * Services with the given status that have both their own and their category's text in the
     * language, ordered by category, then service sort order, then slug (decision SV-05). The inner
     * joins mean a service missing the language is left out rather than shown in another language.
     */
    @Query("""
            select new com.sokhasaclinic.clinicbackend.clinicservice.repository.PublicServiceRow(
                s.id, s.slug, c.slug, ct.name, st.name, st.shortDescription,
                s.durationMinutes, s.priceUsd, s.originalPriceUsd, s.imageMediaId, st.imageAltText)
            from ClinicService s
                join s.category c,
                ServiceCategoryTranslation ct,
                ClinicServiceTranslation st
            where s.status = :status
                and ct.id.ownerId = c.id and ct.id.languageCode = :languageCode
                and st.id.ownerId = s.id and st.id.languageCode = :languageCode
            order by c.sortOrder, s.sortOrder, s.slug
            """)
    List<PublicServiceRow> findPublicRows(
            @Param("status") ServiceStatus status,
            @Param("languageCode") String languageCode);
}
