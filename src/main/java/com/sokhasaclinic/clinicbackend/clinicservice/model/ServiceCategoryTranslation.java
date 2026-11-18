package com.sokhasaclinic.clinicbackend.clinicservice.model;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

/** Category name in one language. */
@Entity
@Table(name = "service_category_translation", schema = "clinic")
@Getter
public class ServiceCategoryTranslation {

    @EmbeddedId
    @AttributeOverride(name = "ownerId", column = @Column(name = "category_id", nullable = false, updatable = false))
    private TranslationId id;

    @Column(nullable = false, length = 120)
    private String name;

    protected ServiceCategoryTranslation() {
    }
}
