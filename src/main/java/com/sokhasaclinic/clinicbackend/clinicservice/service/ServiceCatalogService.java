package com.sokhasaclinic.clinicbackend.clinicservice.service;

import com.sokhasaclinic.clinicbackend.clinicservice.dto.PublicServiceResponse;
import com.sokhasaclinic.clinicbackend.clinicservice.model.ServiceStatus;
import com.sokhasaclinic.clinicbackend.clinicservice.repository.ClinicServiceRepository;
import com.sokhasaclinic.clinicbackend.clinicservice.repository.PublicServiceRow;
import com.sokhasaclinic.clinicbackend.cms.model.CmsMedia;
import com.sokhasaclinic.clinicbackend.cms.repository.MediaRepository;
import com.sokhasaclinic.clinicbackend.cms.service.storage.MediaStorage;
import com.sokhasaclinic.clinicbackend.common.i18n.Language;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * author: san tola
 * Public, read-only list of bookable services (approved November 18, 2026, decisions SV-01 to
 * SV-10). Services are seeded by Flyway; there is no editor in the MVP.
 */
@Service
public class ServiceCatalogService {

    private final ClinicServiceRepository serviceRepository;
    private final MediaRepository mediaRepository;
    private final MediaStorage mediaStorage;

    public ServiceCatalogService(
            ClinicServiceRepository serviceRepository,
            MediaRepository mediaRepository,
            MediaStorage mediaStorage) {
        this.serviceRepository = serviceRepository;
        this.mediaRepository = mediaRepository;
        this.mediaStorage = mediaStorage;
    }

    /**
     * Every active service in {@code language}, in display order. Never falls back to another
     * language (I18N-02); an empty catalogue is an empty list, not an error.
     */
    @Transactional(readOnly = true)
    public List<PublicServiceResponse> listPublicServices(Language language) {
        List<PublicServiceRow> rows = serviceRepository.findPublicRows(ServiceStatus.ACTIVE, language.code());
        Map<UUID, CmsMedia> images = loadImages(rows);
        // Map.of() rejects null keys, so look up only rows that have an image.
        return rows.stream()
                .map(row -> toResponse(row, row.imageMediaId() == null ? null : images.get(row.imageMediaId())))
                .toList();
    }

    /** One query for every image on the page, instead of one per service. */
    private Map<UUID, CmsMedia> loadImages(List<PublicServiceRow> rows) {
        Set<UUID> mediaIds = rows.stream()
                .map(PublicServiceRow::imageMediaId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (mediaIds.isEmpty()) {
            return Map.of();
        }
        return mediaRepository.findAllById(mediaIds).stream()
                .collect(Collectors.toMap(CmsMedia::getId, Function.identity()));
    }

    /**
     * {@code imageUrl} and {@code imageAltText} are both set or both null. A service whose image
     * has no alt text in this language is shown without the image rather than with an
     * inaccessible one.
     */
    private PublicServiceResponse toResponse(PublicServiceRow row, CmsMedia image) {
        boolean showImage = image != null && row.imageAltText() != null;
        return new PublicServiceResponse(
                row.id(),
                row.slug(),
                row.categorySlug(),
                row.categoryName(),
                row.name(),
                row.shortDescription(),
                row.durationMinutes(),
                row.priceUsd(),
                row.originalPriceUsd(),
                showImage ? mediaStorage.signedUrl(image.getObjectKey()) : null,
                showImage ? row.imageAltText() : null);
    }
}
