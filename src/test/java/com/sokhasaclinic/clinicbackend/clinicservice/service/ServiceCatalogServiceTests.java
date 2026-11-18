package com.sokhasaclinic.clinicbackend.clinicservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sokhasaclinic.clinicbackend.clinicservice.dto.PublicServiceResponse;
import com.sokhasaclinic.clinicbackend.clinicservice.model.ServiceStatus;
import com.sokhasaclinic.clinicbackend.clinicservice.repository.ClinicServiceRepository;
import com.sokhasaclinic.clinicbackend.clinicservice.repository.PublicServiceRow;
import com.sokhasaclinic.clinicbackend.cms.model.CmsMedia;
import com.sokhasaclinic.clinicbackend.cms.repository.MediaRepository;
import com.sokhasaclinic.clinicbackend.cms.service.storage.MediaStorage;
import com.sokhasaclinic.clinicbackend.common.i18n.Language;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServiceCatalogServiceTests {

    private ClinicServiceRepository serviceRepository;
    private MediaRepository mediaRepository;
    private MediaStorage mediaStorage;
    private ServiceCatalogService catalog;

    @BeforeEach
    void setUp() {
        serviceRepository = mock(ClinicServiceRepository.class);
        mediaRepository = mock(MediaRepository.class);
        mediaStorage = mock(MediaStorage.class);
        catalog = new ServiceCatalogService(serviceRepository, mediaRepository, mediaStorage);
    }

    @Test
    void readsActiveServicesInTheRequestedLanguageInRepositoryOrder() {
        PublicServiceRow first = row("chronic-disease-management", null, null);
        PublicServiceRow second = row("high-resolution-ultrasound", null, null);
        when(serviceRepository.findPublicRows(ServiceStatus.ACTIVE, "km")).thenReturn(List.of(first, second));

        List<PublicServiceResponse> services = catalog.listPublicServices(Language.KM);

        assertEquals(List.of("chronic-disease-management", "high-resolution-ultrasound"),
                services.stream().map(PublicServiceResponse::slug).toList());
        verify(serviceRepository).findPublicRows(ServiceStatus.ACTIVE, "km");
    }

    @Test
    void serviceWithoutImageHasNullImageFieldsAndSkipsStorage() {
        when(serviceRepository.findPublicRows(ServiceStatus.ACTIVE, "en"))
                .thenReturn(List.of(row("colonoscopy", null, null)));

        PublicServiceResponse service = catalog.listPublicServices(Language.EN).getFirst();

        assertNull(service.imageUrl());
        assertNull(service.imageAltText());
        assertEquals(new BigDecimal("450.00"), service.priceUsd());
        assertNull(service.originalPriceUsd());
        verifyNoInteractions(mediaRepository, mediaStorage);
    }

    @Test
    void imagesAreLoadedInOneQueryAndSigned() {
        CmsMedia media = new CmsMedia(UUID.randomUUID(), "services/colonoscopy.webp", "colonoscopy.webp", "image/webp", 10);
        when(serviceRepository.findPublicRows(ServiceStatus.ACTIVE, "zh")).thenReturn(List.of(
                row("colonoscopy", media.getId(), "结肠镜检查套餐海报"),
                row("ultrasound", null, null)));
        when(mediaRepository.findAllById(Set.of(media.getId()))).thenReturn(List.of(media));
        when(mediaStorage.signedUrl("services/colonoscopy.webp")).thenReturn("https://r2.test/signed");

        List<PublicServiceResponse> services = catalog.listPublicServices(Language.ZH);

        assertEquals("https://r2.test/signed", services.get(0).imageUrl());
        assertEquals("结肠镜检查套餐海报", services.get(0).imageAltText());
        assertNull(services.get(1).imageUrl());
        verify(mediaRepository).findAllById(Set.of(media.getId()));
    }

    @Test
    void imageWithoutAltTextInThisLanguageIsNotShown() {
        UUID mediaId = UUID.randomUUID();
        when(serviceRepository.findPublicRows(ServiceStatus.ACTIVE, "km"))
                .thenReturn(List.of(row("colonoscopy", mediaId, null)));
        when(mediaRepository.findAllById(any())).thenReturn(
                List.of(new CmsMedia(mediaId, "k", "f.webp", "image/webp", 1)));

        PublicServiceResponse service = catalog.listPublicServices(Language.KM).getFirst();

        assertNull(service.imageUrl());
        assertNull(service.imageAltText());
        verifyNoInteractions(mediaStorage);
    }

    @Test
    void noActiveServicesIsAnEmptyList() {
        when(serviceRepository.findPublicRows(ServiceStatus.ACTIVE, "en")).thenReturn(List.of());

        assertEquals(List.of(), catalog.listPublicServices(Language.EN));
    }

    private static PublicServiceRow row(String slug, UUID mediaId, String altText) {
        return new PublicServiceRow(UUID.randomUUID(), slug, "digestive-health", "Digestive Health",
                "Name of " + slug, "Short description.", 60, new BigDecimal("450.00"), null, mediaId, altText);
    }
}
