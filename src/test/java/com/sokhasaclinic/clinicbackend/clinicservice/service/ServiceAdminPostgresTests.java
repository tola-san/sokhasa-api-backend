package com.sokhasaclinic.clinicbackend.clinicservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sokhasaclinic.clinicbackend.clinicservice.dto.AdminServiceDtos.AdminService;
import com.sokhasaclinic.clinicbackend.clinicservice.dto.AdminServiceDtos.AdminServiceText;
import com.sokhasaclinic.clinicbackend.clinicservice.dto.AdminServiceDtos.AdminServiceUpdate;
import com.sokhasaclinic.clinicbackend.clinicservice.dto.AdminServiceDtos.VersionedService;
import com.sokhasaclinic.clinicbackend.clinicservice.dto.PublicServiceResponse;
import com.sokhasaclinic.clinicbackend.clinicservice.model.ServiceStatus;
import com.sokhasaclinic.clinicbackend.cms.exception.CmsException;
import com.sokhasaclinic.clinicbackend.cms.service.storage.MediaStorage;
import com.sokhasaclinic.clinicbackend.common.i18n.Language;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin service editing against a real PostgreSQL database: row locks, versions, translations and
 * the effect on the public list. Set CLINIC_TEST_DATABASE_URL (and _USERNAME / _PASSWORD) to a
 * disposable database to enable it. Each test rolls back.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@EnabledIfEnvironmentVariable(named = "CLINIC_TEST_DATABASE_URL", matches = "jdbc:postgresql:.+")
@Transactional
class ServiceAdminPostgresTests {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CLINIC_TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("CLINIC_TEST_DATABASE_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("CLINIC_TEST_DATABASE_PASSWORD"));
    }

    @MockitoBean
    MediaStorage mediaStorage;

    @Autowired
    ServiceAdminService admin;

    @Autowired
    ServiceCatalogService catalog;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager entityManager;

    private String id;
    private long version;

    @BeforeEach
    void pickChronicCare() {
        AdminService chronic = admin.listServices().getFirst();
        assertEquals("chronic-disease-management", chronic.slug());
        id = chronic.id().toString();
        version = chronic.version();
    }

    @Test
    void listHasEveryServiceInOrderWithAllLanguages() {
        jdbc.update("update clinic.service set status = 'INACTIVE' where slug = 'mced-liquid-biopsy'");
        // The rows read in @BeforeEach are cached in this transaction; read the JDBC change.
        entityManager.clear();

        List<AdminService> services = admin.listServices();

        assertEquals(6, services.size());
        assertEquals("executive-wellness-checkup", services.getLast().slug());
        assertEquals("INACTIVE", services.get(3).status());
        AdminService first = services.getFirst();
        assertEquals("General & Internal Medicine", first.categoryName());
        assertEquals(List.of("km", "en", "zh"), first.translations().stream().map(AdminServiceText::languageCode).toList());
        assertEquals(List.of(), first.missingLanguages());
    }

    @Test
    void priceChangeIsPublicAndBumpsTheVersion() {
        VersionedService saved = admin.updateService(id, etag(version), update(new BigDecimal("40"), null, List.of()));

        assertEquals(version + 1, saved.version());
        assertEquals(new BigDecimal("40.00"), saved.data().priceUsd());
        PublicServiceResponse publicService = catalog.listPublicServices(Language.EN).getFirst();
        assertEquals(new BigDecimal("40.00"), publicService.priceUsd());
        assertEquals(45, publicService.durationMinutes());
    }

    @Test
    void sendingOneLanguageKeepsTheOthers() {
        AdminService before = admin.getService(id).data();

        VersionedService saved = admin.updateService(id, etag(version), update(new BigDecimal("35"), null,
                List.of(new AdminServiceText("zh", "  慢性病管理  ", "新的说明。", null))));

        assertEquals("慢性病管理", text(saved.data(), "zh").name());
        assertEquals(text(before, "km"), text(saved.data(), "km"));
        assertEquals(text(before, "en"), text(saved.data(), "en"));
        assertEquals(version + 1, saved.version());
    }

    @Test
    void staleEtagIsRejected() {
        admin.updateService(id, etag(version), update(new BigDecimal("40"), null, List.of()));

        CmsException stale = assertThrows(CmsException.class,
                () -> admin.updateService(id, etag(version), update(new BigDecimal("50"), null, List.of())));
        assertEquals(412, stale.getStatus());
    }

    @Test
    void missingOrMalformedIfMatchIsRejected() {
        assertEquals(428, assertThrows(CmsException.class,
                () -> admin.updateService(id, null, update(BigDecimal.ONE, null, List.of()))).getStatus());
        assertEquals(400, assertThrows(CmsException.class,
                () -> admin.updateService(id, "3", update(BigDecimal.ONE, null, List.of()))).getStatus());
    }

    @Test
    void promotionPriceMustBeAboveThePrice() {
        assertEquals(400, assertThrows(CmsException.class, () -> admin.updateService(id, etag(version),
                update(new BigDecimal("40"), new BigDecimal("40"), List.of()))).getStatus());
        assertEquals(400, assertThrows(CmsException.class, () -> admin.updateService(id, etag(version),
                update(null, new BigDecimal("40"), List.of()))).getStatus());
    }

    @Test
    void invalidTranslationsAreRejected() {
        List<List<AdminServiceText>> invalid = List.of(
                List.of(new AdminServiceText("fr", "Nom", "Description", null)),
                List.of(new AdminServiceText("en", "A", "B", null), new AdminServiceText("en", "C", "D", null)),
                List.of(new AdminServiceText("en", "   ", "Description", null)),
                List.of(new AdminServiceText("en", "x".repeat(121), "Description", null)),
                List.of(new AdminServiceText("en", "Name", "Description", "Alt text")));
        for (List<AdminServiceText> translations : invalid) {
            assertEquals(400, assertThrows(CmsException.class, () -> admin.updateService(id, etag(version),
                    update(BigDecimal.ONE, null, translations))).getStatus());
        }
    }

    @Test
    void deactivatingHidesTheServiceAndReactivatingShowsIt() {
        VersionedService inactive = admin.changeStatus(id, etag(version), ServiceStatus.INACTIVE);

        assertEquals("INACTIVE", inactive.data().status());
        assertFalse(publicSlugs().contains("chronic-disease-management"));

        VersionedService active = admin.changeStatus(id, etag(inactive.version()), ServiceStatus.ACTIVE);
        assertEquals("ACTIVE", active.data().status());
        assertTrue(publicSlugs().contains("chronic-disease-management"));
    }

    @Test
    void settingTheSameStatusChangesNothing() {
        VersionedService same = admin.changeStatus(id, etag(version), ServiceStatus.ACTIVE);

        assertEquals(version, same.version());
    }

    @Test
    void activatingAServiceMissingALanguageIsRejected() {
        VersionedService inactive = admin.changeStatus(id, etag(version), ServiceStatus.INACTIVE);
        jdbc.update("delete from clinic.service_translation where language_code = 'zh' and service_id = ?::uuid", id);

        CmsException missing = assertThrows(CmsException.class,
                () -> admin.changeStatus(id, etag(inactive.version()), ServiceStatus.ACTIVE));

        assertEquals(409, missing.getStatus());
        assertEquals("zh", missing.getErrors().getFirst().get("languageCode"));
        assertEquals("INACTIVE", admin.getService(id).data().status());
        assertEquals(List.of("zh"), admin.getService(id).data().missingLanguages());
    }

    @Test
    void unknownOrMalformedIdIsNotFound() {
        assertEquals(404, assertThrows(CmsException.class,
                () -> admin.getService("00000000-0000-0000-0000-000000000000")).getStatus());
        assertEquals(404, assertThrows(CmsException.class, () -> admin.getService("not-a-uuid")).getStatus());
        assertEquals(404, assertThrows(CmsException.class,
                () -> admin.changeStatus("not-a-uuid", etag(0), ServiceStatus.ACTIVE)).getStatus());
    }

    private List<String> publicSlugs() {
        return catalog.listPublicServices(Language.EN).stream().map(PublicServiceResponse::slug).toList();
    }

    private static String etag(long version) {
        return "\"" + version + "\"";
    }

    private static AdminServiceUpdate update(BigDecimal price, BigDecimal originalPrice, List<AdminServiceText> texts) {
        return new AdminServiceUpdate(45, 10, price, originalPrice, 1, texts);
    }

    private static AdminServiceText text(AdminService service, String languageCode) {
        return service.translations().stream()
                .filter(text -> text.languageCode().equals(languageCode))
                .findFirst()
                .orElseThrow();
    }
}
