package com.sokhasaclinic.clinicbackend.settings.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "clinic_settings", schema = "clinic")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClinicSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    @Builder.Default
    private String name = "Sokhasa Clinic";

    private String phone;

    private String email;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(nullable = false)
    @Builder.Default
    private String timezone = "Asia/Phnom_Penh";

    @Column(name = "supported_languages", nullable = false)
    @Builder.Default
    private String[] supportedLanguages = new String[]{"km", "en", "zh"};

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
