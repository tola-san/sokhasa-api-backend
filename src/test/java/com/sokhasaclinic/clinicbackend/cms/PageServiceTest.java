package com.sokhasaclinic.clinicbackend.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sokhasaclinic.clinicbackend.cms.dto.PageRequest;
import com.sokhasaclinic.clinicbackend.cms.dto.PublishRequest;
import com.sokhasaclinic.clinicbackend.cms.dto.TranslationDto;
import com.sokhasaclinic.clinicbackend.cms.model.Page;
import com.sokhasaclinic.clinicbackend.cms.model.PageTranslation;
import com.sokhasaclinic.clinicbackend.cms.repository.PageRepository;
import com.sokhasaclinic.clinicbackend.cms.service.PageService;
import com.sokhasaclinic.clinicbackend.common.exception.ConflictException;
import com.sokhasaclinic.clinicbackend.common.exception.PreconditionFailedException;
import com.sokhasaclinic.clinicbackend.common.exception.PreconditionRequiredException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PageServiceTest {

    @Mock
    private PageRepository pageRepository;

    private PageService pageService;

    @BeforeEach
    void setUp() {
        pageService = new PageService(pageRepository);
    }

    @Test
    void createPage_SlugExists_ThrowsConflict() {
        when(pageRepository.existsBySlug("about-us")).thenReturn(true);

        PageRequest req = new PageRequest(
                "about-us",
                List.of(new TranslationDto("en", "About Us", "<p>Content</p>", null))
        );

        assertThrows(ConflictException.class, () -> pageService.createPage(req));
    }

    @Test
    void publishPage_MissingIfMatch_ThrowsPreconditionRequired() {
        UUID pageId = UUID.randomUUID();
        Page page = Page.builder().id(pageId).version(1L).build();
        when(pageRepository.findByIdWithTranslations(pageId)).thenReturn(Optional.of(page));

        PublishRequest req = new PublishRequest(List.of("en"));

        assertThrows(PreconditionRequiredException.class, () -> pageService.publishPage(pageId, req, null));
    }

    @Test
    void publishPage_StaleIfMatch_ThrowsPreconditionFailed() {
        UUID pageId = UUID.randomUUID();
        Page page = Page.builder().id(pageId).version(2L).build();
        when(pageRepository.findByIdWithTranslations(pageId)).thenReturn(Optional.of(page));

        PublishRequest req = new PublishRequest(List.of("en"));

        assertThrows(PreconditionFailedException.class, () -> pageService.publishPage(pageId, req, "\"1\""));
    }

    @Test
    void publishPage_Success() {
        UUID pageId = UUID.randomUUID();
        Page page = Page.builder()
                .id(pageId)
                .slug("about-us")
                .status(Page.Status.DRAFT)
                .version(1L)
                .translations(new ArrayList<>())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        PageTranslation translation = PageTranslation.builder()
                .page(page)
                .languageCode("en")
                .title("About Us")
                .content("<p>Hello</p>")
                .build();
        page.getTranslations().add(translation);

        when(pageRepository.findByIdWithTranslations(pageId)).thenReturn(Optional.of(page));
        when(pageRepository.save(any(Page.class))).thenAnswer(inv -> inv.getArgument(0));

        PublishRequest req = new PublishRequest(List.of("en"));
        Page published = pageService.publishPage(pageId, req, "\"1\"");

        assertEquals(Page.Status.PUBLISHED, published.getStatus());
        assertNotNull(published.getPublishedAt());
        assertEquals("About Us", translation.getPublishedTitle());
    }
}
