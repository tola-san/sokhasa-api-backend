package com.sokhasaclinic.clinicbackend.cms.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

/**
 * Entity representing a CMS Page with multi-language draft and published translations.
 */
@Entity
@Table(name = "cms_pages", schema = "clinic")
@Getter
public class CmsPage {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(nullable = false)
    private long revision = 1;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @ElementCollection
    @CollectionTable(
            name = "cms_translations",
            schema = "clinic",
            joinColumns = @JoinColumn(name = "page_id")
    )
    @OrderColumn(name = "position")
    private List<CmsTranslation> translations = new ArrayList<>();

    protected CmsPage() {
    }

    public CmsPage(String slug) {
        this.slug = Objects.requireNonNull(slug, "slug must not be null");
    }

    public CmsPage(UUID id, String slug) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.slug = Objects.requireNonNull(slug, "slug must not be null");
    }

    /**
     * Increments the revision counter and updates the updatedAt timestamp.
     */
    public void markChanged() {
        this.revision++;
        this.updatedAt = Instant.now();
    }

    /**
     * Checks if the page is currently published.
     */
    public boolean isPublished() {
        return this.publishedAt != null;
    }

    /**
     * Returns the publication status ("PUBLISHED" or "DRAFT").
     */
    public String getStatus() {
        return isPublished() ? "PUBLISHED" : "DRAFT";
    }

    /**
     * Alias for getStatus() to maintain compatibility with existing callers.
     */
    public String status() {
        return getStatus();
    }

    /**
     * Returns all draft (unpublished) translations.
     */
    public List<CmsTranslation> getDraftTranslations() {
        return translations.stream()
                .filter(t -> !t.isPublished())
                .toList();
    }

    /**
     * Alias for getDraftTranslations().
     */
    public List<CmsTranslation> draft() {
        return getDraftTranslations();
    }

    /**
     * Returns all live (published) translations.
     */
    public List<CmsTranslation> getLiveTranslations() {
        return translations.stream()
                .filter(CmsTranslation::isPublished)
                .toList();
    }

    /**
     * Alias for getLiveTranslations().
     */
    public List<CmsTranslation> live() {
        return getLiveTranslations();
    }

    /**
     * Replaces draft translations and updates revision.
     */
    public void updateDrafts(List<CmsTranslation> newDrafts) {
        translations.removeIf(t -> !t.isPublished());
        translations.addAll(newDrafts);
        markChanged();
    }

    /**
     * Replaces published translations with the specified list and sets publishedAt.
     */
    public void publishTranslations(List<CmsTranslation> liveTranslations) {
        translations.removeIf(CmsTranslation::isPublished);
        translations.addAll(liveTranslations);
        this.publishedAt = Instant.now();
        markChanged();
    }

    /**
     * Unpublishes the page, removing live translations and clearing publishedAt.
     */
    public void unpublish() {
        if (this.publishedAt != null) {
            translations.removeIf(CmsTranslation::isPublished);
            this.publishedAt = null;
            markChanged();
        }
    }

    /**
     * Adds a translation directly (e.g. for initial setup or testing).
     */
    public void addTranslation(CmsTranslation translation) {
        translations.add(Objects.requireNonNull(translation, "translation must not be null"));
    }
}
