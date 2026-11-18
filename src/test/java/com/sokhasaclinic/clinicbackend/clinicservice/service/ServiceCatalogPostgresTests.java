package com.sokhasaclinic.clinicbackend.clinicservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sokhasaclinic.clinicbackend.clinicservice.dto.PublicServiceResponse;
import com.sokhasaclinic.clinicbackend.cms.service.storage.MediaStorage;
import com.sokhasaclinic.clinicbackend.common.i18n.Language;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the service migrations and seed against a real PostgreSQL database, so Hibernate schema
 * validation and the JPQL query are checked too. Set CLINIC_TEST_DATABASE_URL (and _USERNAME /
 * _PASSWORD) to a disposable database to enable it. Each test rolls back.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@EnabledIfEnvironmentVariable(named = "CLINIC_TEST_DATABASE_URL", matches = "jdbc:postgresql:.+")
@Transactional
class ServiceCatalogPostgresTests {

    private static final List<String> SEEDED_SLUGS = List.of(
            "chronic-disease-management",
            "high-resolution-ultrasound",
            "cervical-cancer-hpv-screening",
            "mced-liquid-biopsy",
            "cellular-mitochondrial-health",
            "executive-wellness-checkup");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CLINIC_TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("CLINIC_TEST_DATABASE_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("CLINIC_TEST_DATABASE_PASSWORD"));
    }

    @MockitoBean
    MediaStorage mediaStorage;

    @Autowired
    ServiceCatalogService catalog;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void seedListsEveryWebsiteServiceInCategoryOrderInEachLanguage() {
        for (Language language : Language.values()) {
            List<PublicServiceResponse> services = catalog.listPublicServices(language);
            assertEquals(SEEDED_SLUGS, services.stream().map(PublicServiceResponse::slug).toList(), language.code());
            services.forEach(service -> {
                assertTrue(!service.name().isBlank() && !service.categoryName().isBlank());
                assertNull(service.imageUrl());
                assertNull(service.imageAltText());
            });
        }
    }

    @Test
    void textIsInTheRequestedLanguage() {
        PublicServiceResponse en = catalog.listPublicServices(Language.EN).getFirst();
        PublicServiceResponse km = catalog.listPublicServices(Language.KM).getFirst();

        assertEquals("Chronic Disease Management & Internal Care", en.name());
        assertEquals("General & Internal Medicine", en.categoryName());
        assertEquals("ការគ្រប់គ្រងជំងឺរ៉ាំរ៉ៃ និងវេជ្ជសាស្ត្រទូទៅ", km.name());
        assertEquals(en.id(), km.id());
    }

    @Test
    void promotionPriceAndDurationComeFromTheSeed() {
        PublicServiceResponse checkup = catalog.listPublicServices(Language.EN).getLast();

        assertEquals("executive-wellness-checkup", checkup.slug());
        assertEquals(120, checkup.durationMinutes());
        assertEquals(new BigDecimal("155.00"), checkup.priceUsd());
        assertEquals(new BigDecimal("199.00"), checkup.originalPriceUsd());
    }

    @Test
    void inactiveServicesAreLeftOut() {
        jdbc.update("update clinic.service set status = 'INACTIVE' where slug = 'mced-liquid-biopsy'");

        List<String> slugs = catalog.listPublicServices(Language.EN).stream().map(PublicServiceResponse::slug).toList();

        assertEquals(5, slugs.size());
        assertTrue(!slugs.contains("mced-liquid-biopsy"));
    }

    @Test
    void serviceMissingTheLanguageIsLeftOutInsteadOfFallingBack() {
        jdbc.update("delete from clinic.service_translation where language_code = 'zh' and service_id = "
                + "(select id from clinic.service where slug = 'mced-liquid-biopsy')");

        assertEquals(5, catalog.listPublicServices(Language.ZH).size());
        assertEquals(6, catalog.listPublicServices(Language.EN).size());
    }

    @Test
    void noActiveServicesIsAnEmptyList() {
        jdbc.update("update clinic.service set status = 'INACTIVE'");

        assertEquals(List.of(), catalog.listPublicServices(Language.KM));
    }

    @Test
    void databaseRejectsPromotionPriceNotAboveThePrice() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "update clinic.service set original_price_usd = price_usd where slug = 'executive-wellness-checkup'"));
    }
}
