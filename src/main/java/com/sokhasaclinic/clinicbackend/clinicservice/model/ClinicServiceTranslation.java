package com.sokhasaclinic.clinicbackend.clinicservice.model;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

/** Service card text in one language. */
@Entity
@Table(name = "service_translation", schema = "clinic")
@Getter
public class ClinicServiceTranslation {

    @EmbeddedId
    @AttributeOverride(name = "ownerId", column = @Column(name = "service_id", nullable = false, updatable = false))
    private TranslationId id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "short_description", nullable = false, length = 300)
    private String shortDescription;

    /** Required when the service has an image. */
    @Column(name = "image_alt_text", length = 200)
    private String imageAltText;

    protected ClinicServiceTranslation() {
    }
}
