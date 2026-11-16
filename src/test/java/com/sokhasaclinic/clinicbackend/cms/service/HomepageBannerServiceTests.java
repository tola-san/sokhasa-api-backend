package com.sokhasaclinic.clinicbackend.cms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sokhasaclinic.clinicbackend.cms.dto.BannerDtos.BannerUpdate;
import com.sokhasaclinic.clinicbackend.cms.exception.CmsException;
import com.sokhasaclinic.clinicbackend.cms.repository.HomepageBannerRepository;
import com.sokhasaclinic.clinicbackend.cms.repository.MediaRepository;
import com.sokhasaclinic.clinicbackend.cms.service.storage.MediaStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;

/** Request validation that must fail before any database or storage work. */
class HomepageBannerServiceTests {

    private HomepageBannerRepository bannerRepository;
    private MediaService mediaService;
    private HomepageBannerService service;

    @BeforeEach
    void setUp() {
        bannerRepository = mock(HomepageBannerRepository.class);
        mediaService = mock(MediaService.class);
        service = new HomepageBannerService(bannerRepository, mock(MediaRepository.class), mediaService,
                mock(MediaCleanupService.class), mock(MediaStorage.class), mock(TransactionTemplate.class));
    }

    @Test
    void missingIfMatchIs428() {
        assertStatus(428, null, new BannerUpdate(null, null, "Heading", null));
    }

    @Test
    void weakOrMalformedIfMatchIs400() {
        assertStatus(400, "W/\"1\"", new BannerUpdate(null, null, "Heading", null));
        assertStatus(400, "\"01\"", new BannerUpdate(null, null, "Heading", null));
    }

    @Test
    void emptyRequestIs400() {
        assertStatus(400, "\"1\"", new BannerUpdate(null, null, null, null));
        MockMultipartFile emptyFile = new MockMultipartFile("image", "", "application/octet-stream", new byte[0]);
        assertStatus(400, "\"1\"", new BannerUpdate(emptyFile, null, null, null));
    }

    @Test
    void blankAndOverlongTextIs400() {
        assertStatus(400, "\"1\"", new BannerUpdate(null, null, "   ", null));
        assertStatus(400, "\"1\"", new BannerUpdate(null, null, "x".repeat(81), null));
        assertStatus(400, "\"1\"", new BannerUpdate(null, null, null, "x".repeat(201)));
        assertStatus(400, "\"1\"", new BannerUpdate(null, "x".repeat(201), null, null));
    }

    @Test
    void replacementImageRequiresAltText() {
        MockMultipartFile image = new MockMultipartFile("image", "a.png", "image/png", new byte[] {1});
        assertStatus(400, "\"1\"", new BannerUpdate(image, null, null, null));
    }

    private void assertStatus(int status, String ifMatch, BannerUpdate update) {
        CmsException error = assertThrows(CmsException.class, () -> service.publish(ifMatch, update));
        assertEquals(status, error.getStatus());
        verifyNoInteractions(bannerRepository, mediaService);
    }
}
