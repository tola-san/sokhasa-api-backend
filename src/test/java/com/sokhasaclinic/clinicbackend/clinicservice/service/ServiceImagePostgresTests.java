package com.sokhasaclinic.clinicbackend.clinicservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sokhasaclinic.clinicbackend.clinicservice.dto.AdminServiceDtos.AdminService;
import com.sokhasaclinic.clinicbackend.clinicservice.dto.AdminServiceDtos.AdminServiceText;
import com.sokhasaclinic.clinicbackend.clinicservice.dto.AdminServiceDtos.AdminServiceUpdate;
import com.sokhasaclinic.clinicbackend.clinicservice.dto.AdminServiceDtos.ServiceImageAltText;
import com.sokhasaclinic.clinicbackend.clinicservice.dto.AdminServiceDtos.VersionedService;
import com.sokhasaclinic.clinicbackend.clinicservice.dto.PublicServiceResponse;
import com.sokhasaclinic.clinicbackend.cms.exception.CmsException;
import com.sokhasaclinic.clinicbackend.cms.service.storage.MediaStorage;
import com.sokhasaclinic.clinicbackend.common.i18n.Language;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import javax.imageio.ImageIO;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * Service images against a real PostgreSQL database, with the real image validation and R2
 * replaced by a mock (decisions SI-01 to SI-07). Set CLINIC_TEST_DATABASE_URL (and _USERNAME /
 * _PASSWORD) to a disposable database to enable it. Each test rolls back.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@EnabledIfEnvironmentVariable(named = "CLINIC_TEST_DATABASE_URL", matches = "jdbc:postgresql:.+")
@Transactional
class ServiceImagePostgresTests {

    private static final List<ServiceImageAltText> ALT_TEXTS = List.of(
            new ServiceImageAltText("km", "រូបភាពសេវា"),
            new ServiceImageAltText("en", "Doctor reviewing results with a patient"),
            new ServiceImageAltText("zh", "医生与患者讨论结果"));

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

    private String id;
    private long version;

    @BeforeEach
    void pickChronicCare() {
        reset(mediaStorage);
        when(mediaStorage.signedUrl(anyString())).thenAnswer(call -> "https://r2.test/" + call.getArgument(0));
        AdminService chronic = admin.listServices().getFirst();
        id = chronic.id().toString();
        version = chronic.version();
    }

    @Test
    void uploadSetsTheImageAndAltTextInEveryLanguage() throws Exception {
        VersionedService saved = admin.replaceImage(id, etag(version), png(), ALT_TEXTS);

        AdminService service = saved.data();
        assertEquals(version + 1, saved.version());
        String objectKey = jdbc.queryForObject(
                "select m.object_key from clinic.service s join clinic.cms_media m on m.id = s.image_media_id "
                        + "where s.id = ?::uuid", String.class, id);
        assertEquals("https://r2.test/" + objectKey, service.imageUrl());
        assertEquals("Doctor reviewing results with a patient", text(service, "en").imageAltText());
        verify(mediaStorage).upload(eq(objectKey), eq("image/png"), org.mockito.ArgumentMatchers.any());

        PublicServiceResponse km = catalog.listPublicServices(Language.KM).getFirst();
        assertEquals("https://r2.test/" + objectKey, km.imageUrl());
        assertEquals("រូបភាពសេវា", km.imageAltText());
    }

    @Test
    void replacingDeletesTheOldFile() throws Exception {
        VersionedService first = admin.replaceImage(id, etag(version), png(), ALT_TEXTS);
        String firstKey = currentKey();

        admin.replaceImage(id, etag(first.version()), png(), ALT_TEXTS);

        Integer stillThere = jdbc.queryForObject(
                "select count(*) from clinic.cms_media where object_key = ?", Integer.class, firstKey);
        assertEquals(0, stillThere);
        verify(mediaStorage).delete(firstKey);
    }

    @Test
    void removingClearsTheImageAndEveryAltText() throws Exception {
        VersionedService withImage = admin.replaceImage(id, etag(version), png(), ALT_TEXTS);
        String key = currentKey();

        VersionedService removed = admin.removeImage(id, etag(withImage.version()));

        assertNull(removed.data().imageUrl());
        removed.data().translations().forEach(text -> assertNull(text.imageAltText()));
        assertNull(catalog.listPublicServices(Language.EN).getFirst().imageUrl());
        verify(mediaStorage).delete(key);
    }

    @Test
    void removingWithoutAnImageChangesNothing() {
        assertEquals(version, admin.removeImage(id, etag(version)).version());
    }

    @Test
    void altTextCanBeEditedWithPutOnlyWhileThereIsAnImage() throws Exception {
        AdminServiceUpdate zhAlt = new AdminServiceUpdate(30, 0, new BigDecimal("35"), null, 1, List.of(
                new AdminServiceText("zh", "慢性病综合防治与内科长程管理", "说明。", "新的替代文字")));
        assertEquals(400, assertThrows(CmsException.class,
                () -> admin.updateService(id, etag(version), zhAlt)).getStatus());

        VersionedService withImage = admin.replaceImage(id, etag(version), png(), ALT_TEXTS);
        AdminService saved = admin.updateService(id, etag(withImage.version()), zhAlt).data();

        assertEquals("新的替代文字", text(saved, "zh").imageAltText());
        assertEquals("Doctor reviewing results with a patient", text(saved, "en").imageAltText());
        assertEquals(withImage.data().imageUrl(), saved.imageUrl());
    }

    @Test
    void uploadNeedsAltTextInEveryLanguageAndNothingIsStoredOtherwise() {
        CmsException missing = assertThrows(CmsException.class,
                () -> admin.replaceImage(id, etag(version), png(), ALT_TEXTS.subList(0, 2)));
        CmsException blank = assertThrows(CmsException.class, () -> admin.replaceImage(id, etag(version), png(),
                List.of(ALT_TEXTS.get(0), ALT_TEXTS.get(1), new ServiceImageAltText("zh", "  "))));

        assertEquals(400, missing.getStatus());
        assertEquals(400, blank.getStatus());
        verify(mediaStorage, never()).upload(anyString(), anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void serviceMissingALanguageCannotGetAnImage() {
        jdbc.update("update clinic.service set status = 'INACTIVE' where id = ?::uuid", id);
        jdbc.update("delete from clinic.service_translation where language_code = 'zh' and service_id = ?::uuid", id);

        CmsException missing = assertThrows(CmsException.class,
                () -> admin.replaceImage(id, etag(version), png(), ALT_TEXTS));

        assertEquals(409, missing.getStatus());
        verify(mediaStorage, never()).upload(anyString(), anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void staleEtagIsRejectedBeforeUploading() {
        CmsException stale = assertThrows(CmsException.class,
                () -> admin.replaceImage(id, etag(version + 7), png(), ALT_TEXTS));

        assertEquals(412, stale.getStatus());
        verify(mediaStorage, never()).upload(anyString(), anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unsupportedImageIsRejected() {
        MockMultipartFile gif = new MockMultipartFile("image", "x.gif", "image/gif",
                new byte[] {'G', 'I', 'F', '8', '9', 'a', 1, 0, 1, 0, 0, 0, 0, ';'});

        CmsException unsupported = assertThrows(CmsException.class,
                () -> admin.replaceImage(id, etag(version), gif, ALT_TEXTS));

        assertEquals(415, unsupported.getStatus());
        assertNull(admin.getService(id).data().imageUrl());
    }

    private String currentKey() {
        return jdbc.queryForObject("select m.object_key from clinic.service s join clinic.cms_media m "
                + "on m.id = s.image_media_id where s.id = ?::uuid", String.class, id);
    }

    private static MockMultipartFile png() throws Exception {
        BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return new MockMultipartFile("image", "service.png", "image/png", bytes.toByteArray());
    }

    private static String etag(long version) {
        return "\"" + version + "\"";
    }

    private static AdminServiceText text(AdminService service, String languageCode) {
        return service.translations().stream()
                .filter(text -> text.languageCode().equals(languageCode))
                .findFirst()
                .orElseThrow();
    }
}
