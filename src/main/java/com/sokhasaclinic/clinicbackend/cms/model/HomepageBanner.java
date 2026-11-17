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
 * revision 0 means nothing has been published yet. The image is shared by every language.
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
     * Records a publish and advances the revision. A null media ID keeps the current image. The
     * text lives in {@link HomepageBannerTranslation} rows, written in the same transaction.
     */
    public void publish(UUID newMediaId, Instant now) {
        if (newMediaId != null) {
            mediaId = newMediaId;
        }
        if (publishedAt == null) {
            publishedAt = now;
        }
        updatedAt = now;
        revision++;
    }
}
