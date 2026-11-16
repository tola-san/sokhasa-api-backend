package com.sokhasaclinic.clinicbackend.cms.model;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CmsModelTests {

    @Test
    @DisplayName("CmsPage should initialize in DRAFT status with revision 1")
    void pageInitializesAsDraft() {
        CmsPage page = new CmsPage("about-us");

        assertNotNull(page.getId());
        assertEquals("about-us", page.getSlug());
        assertEquals(1, page.getRevision());
        assertEquals("DRAFT", page.getStatus());
        assertFalse(page.isPublished());
        assertNull(page.getPublishedAt());
        assertNotNull(page.getCreatedAt());
        assertNotNull(page.getUpdatedAt());
        assertTrue(page.getTranslations().isEmpty());
    }

    @Test
    @DisplayName("Publishing page sets status to PUBLISHED, advances revision, and updates live translations")
    void publishingPageUpdatesStatusAndRevisions() {
        CmsPage page = new CmsPage("services");
        CmsTranslation draftEn = new CmsTranslation("en", "Services", "<p>Content</p>", null, null, false);
        page.addTranslation(draftEn);

        assertEquals(1, page.getDraftTranslations().size());
        assertEquals(0, page.getLiveTranslations().size());

        CmsTranslation liveEn = draftEn.withPublished(true);
        page.publishTranslations(List.of(liveEn));

        assertEquals("PUBLISHED", page.getStatus());
        assertTrue(page.isPublished());
        assertNotNull(page.getPublishedAt());
        assertEquals(2, page.getRevision());
        assertEquals(1, page.getLiveTranslations().size());
    }

    @Test
    @DisplayName("Unpublishing page clears published translations and sets status to DRAFT")
    void unpublishingPageClearsLiveTranslations() {
        CmsPage page = new CmsPage("faq");
        CmsTranslation draftKm = new CmsTranslation("km", "សំណួរ", "<p>ចម្លើយ</p>", null, null, false);
        page.addTranslation(draftKm);
        page.publishTranslations(List.of(draftKm.withPublished(true)));

        assertEquals(2, page.getRevision());
        assertTrue(page.isPublished());

        page.unpublish();

        assertEquals("DRAFT", page.getStatus());
        assertFalse(page.isPublished());
        assertNull(page.getPublishedAt());
        assertEquals(3, page.getRevision());
        assertEquals(0, page.getLiveTranslations().size());
    }

    @Test
    @DisplayName("CmsMedia encapsulates fields correctly")
    void mediaEncapsulation() {
        UUID id = UUID.randomUUID();
        CmsMedia media = new CmsMedia(id, "media/123", "clinic.jpg", "image/jpeg", 2048);

        assertEquals(id, media.getId());
        assertEquals("media/123", media.getObjectKey());
        assertEquals("clinic.jpg", media.getFileName());
        assertEquals("image/jpeg", media.getMimeType());
        assertEquals(2048, media.getSize());
    }
}
