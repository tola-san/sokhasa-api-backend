package com.sokhasaclinic.clinicbackend.cms.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

/**
 * Embeddable representing a single language translation of a CMS page.
 */
@Embeddable
@Getter
public class CmsTranslation {

    @Column(name = "language_code", nullable = false, length = 2)
    private String languageCode;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "media_id")
    private UUID mediaId;

    @Column(name = "alt_text", length = 200)
    private String altText;

    @Column(nullable = false)
    private boolean published;

    protected CmsTranslation() {
    }

    public CmsTranslation(String languageCode, String title, String content, UUID mediaId, String altText, boolean published) {
        this.languageCode = Objects.requireNonNull(languageCode, "languageCode must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.content = Objects.requireNonNull(content, "content must not be null");
        this.mediaId = mediaId;
        this.altText = altText;
        this.published = published;
    }

    /**
     * Creates a copy of this translation with the given published status.
     */
    public CmsTranslation withPublished(boolean isPublished) {
        return new CmsTranslation(this.languageCode, this.title, this.content, this.mediaId, this.altText, isPublished);
    }
}
