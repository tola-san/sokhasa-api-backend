package com.sokhasaclinic.clinicbackend.cms.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * The single homepage hero banner (first photo slide). Row {@code id = 1} always exists;
 * revision 0 means nothing has been published yet.
 */
@Entity
@Table(name = "cms_homepage_banner", schema = "clinic")
@Getter
public class HomepageBanner {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id;

    @Column(nullable = false)
    private long revision;

    @Column(name = "media_id")
    private UUID mediaId;

    @Column(name = "alt_text", length = 200)
    private String altText;

    @Column(length = 80)
    private String heading;

    @Column(length = 200)
    private String description;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected HomepageBanner() {
    }

    public boolean isPublished() {
        return revision > 0;
    }

    /**
     * Applies the non-null values and advances the revision. Callers validate the values first.
     */
    public void publish(UUID newMediaId, String newAltText, String newHeading, String newDescription, Instant now) {
        if (newMediaId != null) {
            mediaId = newMediaId;
        }
        if (newAltText != null) {
            altText = newAltText;
        }
        if (newHeading != null) {
            heading = newHeading;
        }
        if (newDescription != null) {
            description = newDescription;
        }
        if (publishedAt == null) {
            publishedAt = now;
        }
        updatedAt = now;
        revision++;
    }
}
