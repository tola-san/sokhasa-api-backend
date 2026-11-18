package com.sokhasaclinic.clinicbackend.clinicservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

/**
 * Key of a translation row: the translated row's id and a language code. Column names are set
 * per table with {@code @AttributeOverride}.
 */
@Embeddable
@Getter
public class TranslationId implements Serializable {

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "language_code", length = 2, nullable = false, updatable = false)
    private String languageCode;

    protected TranslationId() {
    }

    public TranslationId(UUID ownerId, String languageCode) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId must not be null");
        this.languageCode = Objects.requireNonNull(languageCode, "languageCode must not be null");
    }

    @Override
    public boolean equals(Object other) {

        return other instanceof TranslationId that
                && ownerId.equals(that.ownerId)
                && languageCode.equals(that.languageCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerId, languageCode);
    }
}
