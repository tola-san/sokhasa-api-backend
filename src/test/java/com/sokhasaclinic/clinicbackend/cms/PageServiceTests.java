package com.sokhasaclinic.clinicbackend.cms;

import com.sokhasaclinic.clinicbackend.cms.dto.CmsDtos;
import com.sokhasaclinic.clinicbackend.cms.dto.PublishPageRequest;
import com.sokhasaclinic.clinicbackend.cms.dto.SavePageRequest;
import com.sokhasaclinic.clinicbackend.cms.dto.TranslationDto;
import com.sokhasaclinic.clinicbackend.cms.exception.CmsException;
import com.sokhasaclinic.clinicbackend.cms.model.CmsPage;
import com.sokhasaclinic.clinicbackend.cms.model.CmsTranslation;
import com.sokhasaclinic.clinicbackend.cms.repository.MediaRepository;
import com.sokhasaclinic.clinicbackend.cms.repository.PageRepository;
import com.sokhasaclinic.clinicbackend.cms.service.PageService;
import com.sokhasaclinic.clinicbackend.cms.service.storage.MediaStorage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PageServiceTests {

    private final PageRepository repository = mock(PageRepository.class);
    private final PageService service = new PageService(
            repository,
            mock(MediaRepository.class),
            mock(MediaStorage.class)
    );

    private CmsPage createTestPage() {
        CmsPage page = new CmsPage("about");
        page.addTranslation(new CmsTranslation("en", "Original", "<p>Original</p>", null, null, false));
        when(repository.lock(page.getId())).thenReturn(Optional.of(page));
        when(repository.findBySlug("about")).thenReturn(Optional.of(page));
        return page;
    }

    @Test
    void draftEditsDoNotChangePublishedContentAndRejectStaleVersions() {
        CmsPage page = createTestPage();
        service.publishPage(page.getId(), "\"1\"", new PublishPageRequest(List.of("en")));
        service.updatePage(
                page.getId(),
                "\"2\"",
                new SavePageRequest("about", List.of(new TranslationDto("en", "Edited", "<p>Edited</p>", null)))
        );

        assertEquals("Original", service.getPublishedPage("about", List.of("en")).title());

        CmsException error = assertThrows(
                CmsException.class,
                () -> service.unpublishPage(page.getId(), "\"2\"")
        );
        assertEquals(412, error.getStatus());
        assertEquals("Original", service.getPublishedPage("about", List.of("en")).title());
    }

    @Test
    void unpublishRetainsDraftAndIsIdempotentWithCurrentVersion() {
        CmsPage page = createTestPage();
        service.publishPage(page.getId(), "\"1\"", new PublishPageRequest(List.of("en")));
        service.unpublishPage(page.getId(), "\"2\"");
        service.unpublishPage(page.getId(), "\"3\"");

        assertEquals(3, page.getRevision());
        assertEquals(1, page.getDraftTranslations().size());
        assertEquals(404, assertThrows(CmsException.class, () -> service.getPublishedPage("about", List.of("en"))).getStatus());
    }

    @Test
    void sanitizationRemovesExecutableMarkup() {
        CmsPage page = createTestPage();
        service.updatePage(
                page.getId(),
                "\"1\"",
                new SavePageRequest(
                        "about",
                        List.of(new TranslationDto("en", "Title", "<p onclick='bad()'>Safe</p><script>bad()</script>", null))
                )
        );

        assertEquals("<p>Safe</p>", page.getDraftTranslations().getFirst().getContent());
    }

    @Test
    void backwardCompatibilityFacadeWorksWithLegacyDtos() {
        CmsPage page = createTestPage();
        service.publish(page.getId(), "\"1\"", new CmsDtos.Publish(List.of("en")));
        service.save(page.getId(), "\"2\"", new CmsDtos.Save("about",
                List.of(new CmsDtos.Translation("en", "Edited", "<p>Edited</p>", null))));
        assertEquals("Original", service.published("about", List.of("en")).title());
    }
}
