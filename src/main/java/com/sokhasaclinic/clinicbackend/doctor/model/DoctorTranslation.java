package com.sokhasaclinic.clinicbackend.doctor.model;

import com.sokhasaclinic.clinicbackend.clinicservice.model.TranslationId;
import com.sokhasaclinic.clinicbackend.common.i18n.Language;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Doctor profile text in one language (PC-05, DP-01, DP-02, DP-06). */
@Entity
@Table(name = "doctor_translation", schema = "clinic")
@Getter
public class DoctorTranslation {

    @EmbeddedId
    @AttributeOverride(name = "ownerId", column = @Column(name = "doctor_id", nullable = false, updatable = false))
    private TranslationId id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 3000)
    private String bio;

    @Column(length = 500)
    private String education;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> tags;

    /** Required in every language while the doctor has a photo. */
    @Column(name = "image_alt_text", length = 200)
    private String imageAltText;

    protected DoctorTranslation() {
    }

    public DoctorTranslation(UUID doctorId, Language language, String title, String bio, String education,
            List<String> tags) {
        this.id = new TranslationId(doctorId, language.code());
        update(title, bio, education, tags);
    }

    /** Callers trim and validate the values first. */
    public void update(String newTitle, String newBio, String newEducation, List<String> newTags) {
        title = newTitle;
        bio = newBio;
        education = newEducation;
        tags = List.copyOf(newTags);
    }

    public void changeImageAltText(String newImageAltText) {
        imageAltText = newImageAltText;
    }

    public List<String> getTags() {
        return tags == null ? List.of() : List.copyOf(tags);
    }
}
