package com.sokhasaclinic.clinicbackend.cms.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sokhasaclinic.clinicbackend.cms.dto.BannerDtos.AdminBanner;
import com.sokhasaclinic.clinicbackend.cms.dto.BannerDtos.BannerText;
import com.sokhasaclinic.clinicbackend.cms.dto.BannerDtos.BannerUpdate;
import com.sokhasaclinic.clinicbackend.cms.dto.BannerDtos.PublicBanner;
import com.sokhasaclinic.clinicbackend.cms.dto.BannerDtos.VersionedBanner;
import com.sokhasaclinic.clinicbackend.cms.exception.CmsException;
import com.sokhasaclinic.clinicbackend.cms.exception.CmsExceptionHandler;
import com.sokhasaclinic.clinicbackend.cms.exception.StorageUnavailableException;
import com.sokhasaclinic.clinicbackend.cms.service.HomepageBannerService;
import com.sokhasaclinic.clinicbackend.common.i18n.Language;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HomepageBannerControllerTests {

    private static final String ADMIN = "/api/v1/admin/homepage/banner";
    private static final String PUBLIC = "/api/v1/public/homepage/banner";
    private static final String KM_HEADING = "ការថែទាំដោយក្តីយកចិត្តទុកដាក់";

    private HomepageBannerService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(HomepageBannerService.class);
        mvc = MockMvcBuilders.standaloneSetup(
                        new BannerController(service),
                        new PublicHomepageBannerController(service))
                .setControllerAdvice(new CmsExceptionHandler())
                .build();
    }

    @Test
    void emptyBannerHasInitialEtagAndListsEveryLanguageAsMissing() throws Exception {
        when(service.getAdminBanner())
                .thenReturn(new VersionedBanner(AdminBanner.empty(List.of("km", "en", "zh")), 0));

        mvc.perform(get(ADMIN))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.imageUrl").isEmpty())
                .andExpect(jsonPath("$.data.translations").isEmpty())
                .andExpect(jsonPath("$.data.missingLanguages[0]").value("km"))
                .andExpect(jsonPath("$.data.missingLanguages[2]").value("zh"));
    }

    @Test
    void patchReadsImageAndUtf8TranslationsJson() throws Exception {
        AdminBanner banner = new AdminBanner("https://r2/signed", "clinic-team.webp",
                Instant.parse("2026-11-16T09:30:00Z"),
                List.of(new BannerText("km", KM_HEADING, "ការពិពណ៌នា", "រូបភាព")),
                List.of());
        when(service.publish(eq("\"0\""), any())).thenReturn(new VersionedBanner(banner, 1));

        mvc.perform(multipart(HttpMethod.PATCH, ADMIN)
                        .file(new MockMultipartFile("image", "clinic-team.webp", "image/webp", new byte[] {1, 2}))
                        .file(json("""
                                [{"languageCode":"km","heading":"%s","description":"ការពិពណ៌នា","altText":"រូបភាព"}]
                                """.formatted(KM_HEADING)))
                        .header("If-Match", "\"0\""))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.data.translations[0].heading").value(KM_HEADING))
                .andExpect(jsonPath("$.data.fileName").value("clinic-team.webp"));

        ArgumentCaptor<BannerUpdate> update = ArgumentCaptor.forClass(BannerUpdate.class);
        verify(service).publish(eq("\"0\""), update.capture());
        assertEquals(KM_HEADING, update.getValue().translations().getFirst().heading());
        assertEquals("km", update.getValue().translations().getFirst().languageCode());
        assertEquals("clinic-team.webp", update.getValue().image().getOriginalFilename());
    }

    @Test
    void omittedPartsAreNull() throws Exception {
        when(service.publish(any(), any())).thenReturn(new VersionedBanner(AdminBanner.empty(List.of()), 2));

        mvc.perform(multipart(HttpMethod.PATCH, ADMIN)
                        .file(new MockMultipartFile("image", "a.png", "image/png", new byte[] {1}))
                        .header("If-Match", "\"1\""))
                .andExpect(status().isOk());

        ArgumentCaptor<BannerUpdate> update = ArgumentCaptor.forClass(BannerUpdate.class);
        verify(service).publish(eq("\"1\""), update.capture());
        assertNull(update.getValue().translations());
    }

    @Test
    void malformedTranslationsJsonIs400() throws Exception {
        mvc.perform(multipart(HttpMethod.PATCH, ADMIN)
                        .file(json("[{\"languageCode\":"))
                        .header("If-Match", "\"1\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @Test
    void missingTranslationReturns409WithLanguageErrors() throws Exception {
        when(service.publish(any(), any()))
                .thenThrow(CmsException.translationMissing(Set.of(Language.ZH)));

        mvc.perform(multipart(HttpMethod.PATCH, ADMIN)
                        .file(json("[]"))
                        .header("If-Match", "\"1\""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("TRANSLATION_MISSING"))
                .andExpect(jsonPath("$.errors[0].field").value("translations"))
                .andExpect(jsonPath("$.errors[0].languageCode").value("zh"));
    }

    @Test
    void staleVersionReturns412ProblemDetails() throws Exception {
        when(service.publish(any(), any())).thenThrow(CmsException.versionMismatch());

        mvc.perform(multipart(HttpMethod.PATCH, ADMIN).file(json("[]")).header("If-Match", "\"1\""))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.type").value("VERSION_MISMATCH"));
    }

    @Test
    void missingIfMatchReturns428() throws Exception {
        when(service.publish(eq(null), any())).thenThrow(CmsException.versionRequired());

        mvc.perform(multipart(HttpMethod.PATCH, ADMIN).file(json("[]")))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.type").value("VERSION_REQUIRED"));
    }

    @Test
    void storageFailureReturns503ProblemDetails() throws Exception {
        when(service.publish(any(), any())).thenThrow(new StorageUnavailableException("Media storage is not configured."));

        mvc.perform(multipart(HttpMethod.PATCH, ADMIN).file(json("[]")).header("If-Match", "\"1\""))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("STORAGE_UNAVAILABLE"));
    }

    @Test
    void publicBannerReturnsOneLanguageWithContentLanguage() throws Exception {
        when(service.getPublicBanner(Language.KM)).thenReturn(
                new PublicBanner("km", "https://r2/signed", KM_HEADING, "ការពិពណ៌នា", "រូបភាព"));

        mvc.perform(get(PUBLIC).param("lang", "km"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Language", "km"))
                .andExpect(jsonPath("$.data.languageCode").value("km"))
                .andExpect(jsonPath("$.data.imageUrl").value("https://r2/signed"))
                .andExpect(jsonPath("$.data.heading").value(KM_HEADING));
    }

    @Test
    void publicBannerRejectsMissingOrUnsupportedLanguage() throws Exception {
        mvc.perform(get(PUBLIC)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("VALIDATION_ERROR"));
        for (String lang : List.of("fr", "KM", "km-KH", "")) {
            mvc.perform(get(PUBLIC).param("lang", lang)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void publicBannerIs404WhenLanguageIsNotPublished() throws Exception {
        when(service.getPublicBanner(Language.ZH)).thenThrow(CmsException.bannerNotFound(Language.ZH));

        mvc.perform(get(PUBLIC).param("lang", "zh"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("BANNER_NOT_FOUND"));
    }

    private static MockMultipartFile json(String body) {
        return new MockMultipartFile("translations", "", "application/json", body.getBytes(StandardCharsets.UTF_8));
    }
}
