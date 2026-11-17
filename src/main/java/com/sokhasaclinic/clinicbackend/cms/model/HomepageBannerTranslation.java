package com.sokhasaclinic.clinicbackend.cms.model;

import com.sokhasaclinic.clinicbackend.common.i18n.Language;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * Banner text in one language. There is at most one row per language; writers hold the banner
 * row lock, so translation rows change together with the banner revision.
 */
@Entity
@Table(name = "cms_homepage_banner_translation", schema = "clinic")
@Getter
public class HomepageBannerTranslation {

    @Id
    @Column(name = "language_code", length = 2, nullable = false, updatable = false)
    private String languageCode;

    @Column(nullable = false, length = 120)
    private String heading;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(name = "alt_text", nullable = false, length = 200)
    private String altText;

    protected HomepageBannerTranslation() {
    }

    public HomepageBannerTranslation(Language language, String heading, String description, String altText) {
        this.languageCode = language.code();
        update(heading, description, altText);
    }

    /** Callers validate the values first. */
    public void update(String newHeading, String newDescription, String newAltText) {
        heading = newHeading;
        description = newDescription;
        altText = newAltText;
    }
}
