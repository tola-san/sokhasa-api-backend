package com.sokhasaclinic.clinicbackend.cms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sokhasaclinic.clinicbackend.cms.dto.BannerDtos.BannerText;
import com.sokhasaclinic.clinicbackend.cms.dto.BannerDtos.BannerUpdate;
import com.sokhasaclinic.clinicbackend.cms.dto.BannerDtos.VersionedBanner;
import com.sokhasaclinic.clinicbackend.cms.exception.CmsException;
import com.sokhasaclinic.clinicbackend.cms.service.storage.MediaStorage;
import com.sokhasaclinic.clinicbackend.common.i18n.Language;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Runs the Flyway migrations and the banner publish flow against a real PostgreSQL database.
 * Set CLINIC_TEST_DATABASE_URL (and _USERNAME / _PASSWORD) to a disposable database to enable it.
 * R2 is replaced by a mock.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@EnabledIfEnvironmentVariable(named = "CLINIC_TEST_DATABASE_URL", matches = "jdbc:postgresql:.+")
class HomepageBannerPostgresTests {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CLINIC_TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("CLINIC_TEST_DATABASE_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("CLINIC_TEST_DATABASE_PASSWORD"));
    }

    @MockitoBean
    MediaStorage mediaStorage;

    @Autowired
    HomepageBannerService service;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void resetBanner() {
        jdbc.update("delete from clinic.cms_homepage_banner_translation");
        jdbc.update("update clinic.cms_homepage_banner set revision = 0, media_id = null, "
                + "published_at = null, updated_at = null");
        jdbc.update("delete from clinic.cms_media");
        jdbc.update("delete from clinic.cms_upload_cleanup");
        reset(mediaStorage);
        when(mediaStorage.signedUrl(anyString())).thenAnswer(call -> "https://r2.test/" + call.getArgument(0));
    }

    @Test
    void migrationsMoveBannerTextIntoTranslations() {
        Integer textColumns = jdbc.queryForObject(
                "select count(*) from information_schema.columns where table_schema = 'clinic' "
                        + "and table_name = 'cms_homepage_banner' "
                        + "and column_name in ('heading', 'description', 'alt_text')", Integer.class);
        assertEquals(0, textColumns);
        VersionedBanner empty = service.getAdminBanner();
        assertEquals(0L, empty.revision());
        assertEquals(List.of(), empty.data().translations());
        assertEquals(List.of("km", "en", "zh"), empty.data().missingLanguages());
        assertEquals(404, assertThrows(CmsException.class, () -> service.getPublicBanner(Language.KM)).getStatus());
    }

    @Test
    void firstPublishNeedsImageAndEveryLanguage() throws Exception {
        CmsException noImage = assertThrows(CmsException.class,
                () -> service.publish("\"0\"", new BannerUpdate(null, all("Heading"))));
        assertEquals(400, noImage.getStatus());

        CmsException englishOnly = assertThrows(CmsException.class, () -> service.publish("\"0\"",
                new BannerUpdate(null, List.of(text("en", "Heading")))));
        assertEquals(400, englishOnly.getStatus());

        verify(mediaStorage, never()).upload(anyString(), anyString(), org.mockito.ArgumentMatchers.any());
        assertEquals(404, assertThrows(CmsException.class, () -> service.getPublicBanner(Language.EN)).getStatus());
    }

    @Test
    void publishEditOneLanguageReplaceImageAndRejectStaleVersion() throws Exception {
        VersionedBanner first = service.publish("\"0\"", new BannerUpdate(png("clinic-team.png"), all("Comfort")));
        assertEquals(1L, first.revision());
        assertEquals(List.of("km", "en", "zh"),
                first.data().translations().stream().map(BannerText::languageCode).toList());
        assertEquals(List.of(), first.data().missingLanguages());
        String firstKey = onlyMediaKey();
        assertEquals("https://r2.test/" + firstKey, service.getPublicBanner(Language.KM).imageUrl());
        assertEquals("Comfort km", service.getPublicBanner(Language.KM).heading());
        assertEquals("Comfort zh", service.getPublicBanner(Language.ZH).heading());

        VersionedBanner zhOnly = service.publish("\"1\"",
                new BannerUpdate(null, List.of(text("zh", "新标题"))));
        assertEquals(2L, zhOnly.revision());
        assertEquals("新标题", service.getPublicBanner(Language.ZH).heading());
        assertEquals("Comfort km", service.getPublicBanner(Language.KM).heading());
        assertEquals(firstKey, onlyMediaKey());

        CmsException stale = assertThrows(CmsException.class,
                () -> service.publish("\"1\"", new BannerUpdate(null, List.of(text("en", "Lost update")))));
        assertEquals(412, stale.getStatus());
        assertEquals("Comfort en", service.getPublicBanner(Language.EN).heading());

        VersionedBanner replaced = service.publish("\"2\"", new BannerUpdate(png("lobby.png"), all("Lobby")));
        assertEquals(3L, replaced.revision());
        assertEquals("lobby.png", replaced.data().fileName());
        assertEquals("Lobby en", service.getPublicBanner(Language.EN).altText());
        String secondKey = onlyMediaKey();
        assertNotEquals(firstKey, secondKey);
        verify(mediaStorage).delete(firstKey);
    }

    @Test
    void englishOnlyBannerFromMigrationServesEnglishAndReportsMissing() throws Exception {
        service.publish("\"0\"", new BannerUpdate(png("a.png"), all("Heading")));
        // Simulate the state V4 leaves behind for a banner published before translation existed.
        jdbc.update("delete from clinic.cms_homepage_banner_translation where language_code <> 'en'");

        assertEquals("Heading en", service.getPublicBanner(Language.EN).heading());
        assertEquals(404, assertThrows(CmsException.class, () -> service.getPublicBanner(Language.KM)).getStatus());
        assertEquals(List.of("km", "zh"), service.getAdminBanner().data().missingLanguages());

        CmsException kmOnly = assertThrows(CmsException.class,
                () -> service.publish("\"1\"", new BannerUpdate(null, List.of(text("km", "ចំណងជើង")))));
        assertEquals(409, kmOnly.getStatus());
        assertEquals("TRANSLATION_MISSING", kmOnly.getType());
        assertEquals(404, assertThrows(CmsException.class, () -> service.getPublicBanner(Language.KM)).getStatus());

        service.publish("\"1\"", new BannerUpdate(null, List.of(text("km", "ចំណងជើង"), text("zh", "标题"))));
        assertEquals(List.of(), service.getAdminBanner().data().missingLanguages());
        assertEquals("ចំណងជើង", service.getPublicBanner(Language.KM).heading());
    }

    @Test
    void failedReplacementDeleteIsQueuedForCleanup() throws Exception {
        service.publish("\"0\"", new BannerUpdate(png("a.png"), all("Heading")));
        String firstKey = onlyMediaKey();
        org.mockito.Mockito.doThrow(new RuntimeException("R2 down")).when(mediaStorage).delete(firstKey);

        service.publish("\"1\"", new BannerUpdate(png("b.png"), all("Heading 2")));

        Integer queued = jdbc.queryForObject(
                "select count(*) from clinic.cms_upload_cleanup where object_key = ? and retry_after <= now()",
                Integer.class, firstKey);
        assertEquals(1, queued);
    }

    @Test
    void concurrentSavesWithSameVersionPublishOnce() throws Exception {
        service.publish("\"0\"", new BannerUpdate(png("a.png"), all("Heading")));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (String heading : List.of("From admin A", "From admin B")) {
            Callable<Integer> save = () -> {
                start.await();
                try {
                    service.publish("\"1\"", new BannerUpdate(null, List.of(text("en", heading))));
                    return 200;
                } catch (CmsException ex) {
                    return ex.getStatus();
                }
            };
            results.add(pool.submit(save));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get());
        }
        pool.shutdown();

        statuses.sort(Integer::compare);
        assertEquals(List.of(200, 412), statuses);
        assertEquals(2L, service.getAdminBanner().revision());
    }

    private static List<BannerText> all(String heading) {
        return List.of(text("km", heading + " km"), text("en", heading + " en"), text("zh", heading + " zh"));
    }

    private static BannerText text(String language, String heading) {
        return new BannerText(language, heading, "Description " + language, heading);
    }

    private String onlyMediaKey() {
        return jdbc.queryForObject("select object_key from clinic.cms_media", String.class);
    }

    private static MockMultipartFile png(String name) throws Exception {
        return new MockMultipartFile("image", name, "image/png", MediaServiceTests.samplePng(40, 30));
    }
}
