package com.sokhasaclinic.clinicbackend.clinicservice.model;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import com.sokhasaclinic.clinicbackend.common.i18n.Language;
import java.util.UUID;
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

    public ClinicServiceTranslation(UUID serviceId, Language language, String name, String shortDescription) {
        this.id = new TranslationId(serviceId, language.code());
        update(name, shortDescription);
    }

    /** Callers trim and validate the values first. */
    public void update(String newName, String newShortDescription) {
        name = newName;
        shortDescription = newShortDescription;
    }

    /** Alt text of the service image in this language; {@code null} when the service has no image. */
    public void changeImageAltText(String newImageAltText) {
        imageAltText = newImageAltText;
    }
}
