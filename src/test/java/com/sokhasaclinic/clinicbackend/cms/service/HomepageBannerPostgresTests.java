package com.sokhasaclinic.clinicbackend.cms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sokhasaclinic.clinicbackend.cms.dto.BannerDtos.BannerUpdate;
import com.sokhasaclinic.clinicbackend.cms.dto.BannerDtos.VersionedBanner;
import com.sokhasaclinic.clinicbackend.cms.exception.CmsException;
import com.sokhasaclinic.clinicbackend.cms.service.storage.MediaStorage;
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
        jdbc.update("update clinic.cms_homepage_banner set revision = 0, media_id = null, alt_text = null, "
                + "heading = null, description = null, published_at = null, updated_at = null");
        jdbc.update("delete from clinic.cms_media");
        jdbc.update("delete from clinic.cms_upload_cleanup");
        reset(mediaStorage);
        when(mediaStorage.signedUrl(anyString())).thenAnswer(call -> "https://r2.test/" + call.getArgument(0));
    }

    @Test
    void migrationsReplacePagesWithBanner() {
        Integer pageTables = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'clinic' "
                        + "and table_name in ('cms_pages', 'cms_translations')", Integer.class);
        assertEquals(0, pageTables);
        assertEquals(0L, service.getAdminBanner().revision());
        assertNull(service.getAdminBanner().data().heading());
        CmsException missing = assertThrows(CmsException.class, service::getPublicBanner);
        assertEquals(404, missing.getStatus());
    }

    @Test
    void firstPublishRequiresAllFields() throws Exception {
        CmsException error = assertThrows(CmsException.class, () -> service.publish("\"0\"",
                new BannerUpdate(png("clinic-team.png"), "Alt", "Heading", null)));
        assertEquals(400, error.getStatus());
        verify(mediaStorage, never()).upload(anyString(), anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void publishEditCaptionReplaceImageAndRejectStaleVersion() throws Exception {
        VersionedBanner first = service.publish("\"0\"", new BannerUpdate(png("clinic-team.png"),
                "Sokhasa Clinic staff standing outside the clinic", "Your Comfort, Our Priority",
                "Experience gentle, personalized care designed for your peace of mind."));
        assertEquals(1L, first.revision());
        assertEquals("clinic-team.png", first.data().fileName());
        String firstKey = onlyMediaKey();
        assertEquals("https://r2.test/" + firstKey, service.getPublicBanner().imageUrl());

        VersionedBanner caption = service.publish("\"1\"", new BannerUpdate(null, null, "New heading", null));
        assertEquals(2L, caption.revision());
        assertEquals("New heading", caption.data().heading());
        assertEquals("clinic-team.png", caption.data().fileName());
        assertEquals(firstKey, onlyMediaKey());

        CmsException stale = assertThrows(CmsException.class,
                () -> service.publish("\"1\"", new BannerUpdate(null, null, "Lost update", null)));
        assertEquals(412, stale.getStatus());
        assertEquals("New heading", service.getPublicBanner().heading());

        CmsException noAlt = assertThrows(CmsException.class,
                () -> service.publish("\"2\"", new BannerUpdate(png("lobby.png"), null, null, null)));
        assertEquals(400, noAlt.getStatus());

        VersionedBanner replaced = service.publish("\"2\"",
                new BannerUpdate(png("lobby.png"), "Clinic lobby", null, null));
        assertEquals(3L, replaced.revision());
        assertEquals("lobby.png", replaced.data().fileName());
        assertEquals("New heading", replaced.data().heading());
        String secondKey = onlyMediaKey();
        assertNotEquals(firstKey, secondKey);
        verify(mediaStorage).delete(firstKey);
    }

    @Test
    void failedReplacementDeleteIsQueuedForCleanup() throws Exception {
        service.publish("\"0\"", new BannerUpdate(png("a.png"), "Alt", "Heading", "Description"));
        String firstKey = onlyMediaKey();
        org.mockito.Mockito.doThrow(new RuntimeException("R2 down")).when(mediaStorage).delete(firstKey);

        service.publish("\"1\"", new BannerUpdate(png("b.png"), "Alt 2", null, null));

        Integer queued = jdbc.queryForObject(
                "select count(*) from clinic.cms_upload_cleanup where object_key = ? and retry_after <= now()",
                Integer.class, firstKey);
        assertEquals(1, queued);
    }

    @Test
    void concurrentSavesWithSameVersionPublishOnce() throws Exception {
        service.publish("\"0\"", new BannerUpdate(png("a.png"), "Alt", "Heading", "Description"));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (String heading : List.of("From admin A", "From admin B")) {
            Callable<Integer> save = () -> {
                start.await();
                try {
                    service.publish("\"1\"", new BannerUpdate(null, null, heading, null));
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

    private String onlyMediaKey() {
        return jdbc.queryForObject("select object_key from clinic.cms_media", String.class);
    }

    private static MockMultipartFile png(String name) throws Exception {
        return new MockMultipartFile("image", name, "image/png", MediaServiceTests.samplePng(40, 30));
    }
}
