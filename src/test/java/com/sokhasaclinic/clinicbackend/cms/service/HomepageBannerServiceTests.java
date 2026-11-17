package com.sokhasaclinic.clinicbackend.cms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sokhasaclinic.clinicbackend.cms.dto.BannerDtos.BannerText;
import com.sokhasaclinic.clinicbackend.cms.dto.BannerDtos.BannerUpdate;
import com.sokhasaclinic.clinicbackend.cms.exception.CmsException;
import com.sokhasaclinic.clinicbackend.cms.repository.HomepageBannerRepository;
import com.sokhasaclinic.clinicbackend.cms.repository.HomepageBannerTranslationRepository;
import com.sokhasaclinic.clinicbackend.cms.repository.MediaRepository;
import com.sokhasaclinic.clinicbackend.cms.service.storage.MediaStorage;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;

/** Request validation that must fail before any database or storage work. */
class HomepageBannerServiceTests {

    private HomepageBannerRepository bannerRepository;
    private HomepageBannerTranslationRepository translationRepository;
    private MediaService mediaService;
    private HomepageBannerService service;

    @BeforeEach
    void setUp() {
        bannerRepository = mock(HomepageBannerRepository.class);
        translationRepository = mock(HomepageBannerTranslationRepository.class);
        mediaService = mock(MediaService.class);
        service = new HomepageBannerService(bannerRepository, translationRepository, mock(MediaRepository.class),
                mediaService, mock(MediaCleanupService.class), mock(MediaStorage.class),
                mock(TransactionTemplate.class));
    }

    @Test
    void missingIfMatchIs428() {
        assertStatus(428, null, update(null, text("en")));
    }

    @Test
    void weakOrMalformedIfMatchIs400() {
        assertStatus(400, "W/\"1\"", update(null, text("en")));
        assertStatus(400, "\"01\"", update(null, text("en")));
    }

    @Test
    void emptyRequestIs400() {
        assertStatus(400, "\"1\"", new BannerUpdate(null, null));
        assertStatus(400, "\"1\"", new BannerUpdate(null, List.of()));
        MockMultipartFile emptyFile = new MockMultipartFile("image", "", "application/octet-stream", new byte[0]);
        assertStatus(400, "\"1\"", new BannerUpdate(emptyFile, null));
    }

    @Test
    void unsupportedOrDuplicateLanguageIs400() {
        assertStatus(400, "\"1\"", update(null, text("fr")));
        assertStatus(400, "\"1\"", update(null, text("KM")));
        assertStatus(400, "\"1\"", update(null, text(null)));
        assertStatus(400, "\"1\"", update(null, text("km"), text("km")));
        assertStatus(400, "\"1\"", new BannerUpdate(null, Arrays.asList((BannerText) null)));
    }

    @Test
    void blankOrMissingFieldIs400() {
        assertStatus(400, "\"1\"", update(null, new BannerText("en", "   ", "Description", "Alt")));
        assertStatus(400, "\"1\"", update(null, new BannerText("en", "Heading", null, "Alt")));
        assertStatus(400, "\"1\"", update(null, new BannerText("en", "Heading", "Description", "")));
    }

    @Test
    void textOverPerLanguageLimitIs400() {
        assertStatus(400, "\"1\"", update(null, new BannerText("km", "ក".repeat(121), "Description", "Alt")));
        assertStatus(400, "\"1\"", update(null, new BannerText("en", "Heading", "x".repeat(301), "Alt")));
        assertStatus(400, "\"1\"", update(null, new BannerText("zh", "Heading", "Description", "字".repeat(201))));
    }

    @Test
    void replacementImageRequiresEveryLanguage() {
        MockMultipartFile image = new MockMultipartFile("image", "a.png", "image/png", new byte[] {1});
        assertStatus(400, "\"1\"", new BannerUpdate(image, null));
        assertStatus(400, "\"1\"", update(image, text("km"), text("en")));
    }

    private static BannerUpdate update(MockMultipartFile image, BannerText... translations) {
        return new BannerUpdate(image, List.of(translations));
    }

    private static BannerText text(String languageCode) {
        return new BannerText(languageCode, "Heading", "Description", "Alt");
    }

    private void assertStatus(int status, String ifMatch, BannerUpdate update) {
        CmsException error = assertThrows(CmsException.class, () -> service.publish(ifMatch, update));
        assertEquals(status, error.getStatus());
        verifyNoInteractions(bannerRepository, translationRepository, mediaService);
    }
}
