package com.sokhasaclinic.clinicbackend.cms.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sokhasaclinic.clinicbackend.cms.dto.BannerDtos.AdminBanner;
import com.sokhasaclinic.clinicbackend.cms.dto.BannerDtos.BannerUpdate;
import com.sokhasaclinic.clinicbackend.cms.dto.BannerDtos.PublicBanner;
import com.sokhasaclinic.clinicbackend.cms.dto.BannerDtos.VersionedBanner;
import com.sokhasaclinic.clinicbackend.cms.exception.CmsException;
import com.sokhasaclinic.clinicbackend.cms.exception.CmsExceptionHandler;
import com.sokhasaclinic.clinicbackend.cms.exception.StorageUnavailableException;
import com.sokhasaclinic.clinicbackend.cms.service.HomepageBannerService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HomepageBannerControllerTests {

    private static final String ADMIN = "/api/v1/admin/homepage/banner";
    private static final String PUBLIC = "/api/v1/public/homepage/banner";

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
    void emptyBannerHasInitialEtag() throws Exception {
        when(service.getAdminBanner()).thenReturn(new VersionedBanner(AdminBanner.empty(), 0));

        mvc.perform(get(ADMIN))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.heading").isEmpty())
                .andExpect(jsonPath("$.data.imageUrl").isEmpty());
    }

    @Test
    void patchReadsUtf8TextPartsAndImage() throws Exception {
        AdminBanner banner = new AdminBanner("https://r2/signed", "clinic-team.webp", "Staff outside",
                "Your Comfort — Our Priority", "Gentle care", Instant.parse("2026-11-16T09:30:00Z"));
        when(service.publish(eq("\"0\""), any())).thenReturn(new VersionedBanner(banner, 1));

        mvc.perform(multipart(HttpMethod.PATCH, ADMIN)
                        .file(new MockMultipartFile("image", "clinic-team.webp", "image/webp", new byte[] {1, 2}))
                        .part(text("altText", "Staff outside"))
                        .part(text("heading", "Your Comfort — Our Priority"))
                        .part(text("description", "Gentle care"))
                        .header("If-Match", "\"0\""))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.data.heading").value("Your Comfort — Our Priority"))
                .andExpect(jsonPath("$.data.fileName").value("clinic-team.webp"));

        ArgumentCaptor<BannerUpdate> update = ArgumentCaptor.forClass(BannerUpdate.class);
        verify(service).publish(eq("\"0\""), update.capture());
        assertEquals("Your Comfort — Our Priority", update.getValue().heading());
        assertEquals("clinic-team.webp", update.getValue().image().getOriginalFilename());
    }

    @Test
    void omittedPartsAreNull() throws Exception {
        when(service.publish(any(), any())).thenReturn(new VersionedBanner(AdminBanner.empty(), 2));

        mvc.perform(multipart(HttpMethod.PATCH, ADMIN)
                        .part(text("heading", "New heading"))
                        .header("If-Match", "\"1\""))
                .andExpect(status().isOk());

        ArgumentCaptor<BannerUpdate> update = ArgumentCaptor.forClass(BannerUpdate.class);
        verify(service).publish(eq("\"1\""), update.capture());
        assertNull(update.getValue().image());
        assertNull(update.getValue().altText());
        assertNull(update.getValue().description());
    }

    @Test
    void staleVersionReturns412ProblemDetails() throws Exception {
        when(service.publish(any(), any())).thenThrow(CmsException.versionMismatch());

        mvc.perform(multipart(HttpMethod.PATCH, ADMIN)
                        .part(text("heading", "New heading"))
                        .header("If-Match", "\"1\""))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.type").value("VERSION_MISMATCH"));
    }

    @Test
    void missingIfMatchReturns428() throws Exception {
        when(service.publish(eq(null), any())).thenThrow(CmsException.versionRequired());

        mvc.perform(multipart(HttpMethod.PATCH, ADMIN).part(text("heading", "New heading")))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.type").value("VERSION_REQUIRED"));
    }

    @Test
    void storageFailureReturns503ProblemDetails() throws Exception {
        when(service.publish(any(), any())).thenThrow(new StorageUnavailableException("Media storage is not configured."));

        mvc.perform(multipart(HttpMethod.PATCH, ADMIN)
                        .part(text("heading", "New heading"))
                        .header("If-Match", "\"1\""))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("STORAGE_UNAVAILABLE"));
    }

    @Test
    void publicBannerReturnsCaptionFields() throws Exception {
        when(service.getPublicBanner()).thenReturn(
                new PublicBanner("https://r2/signed", "Staff outside", "Heading", "Description"));

        mvc.perform(get(PUBLIC))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.imageUrl").value("https://r2/signed"))
                .andExpect(jsonPath("$.data.description").value("Description"));
    }

    @Test
    void publicBannerIs404BeforeFirstPublish() throws Exception {
        when(service.getPublicBanner()).thenThrow(CmsException.bannerNotFound());

        mvc.perform(get(PUBLIC))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("BANNER_NOT_FOUND"));
    }

    private static MockPart text(String name, String value) {
        return new MockPart(name, value.getBytes(StandardCharsets.UTF_8));
    }
}
