package com.sokhasaclinic.clinicbackend.cms.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "page_translations", schema = "clinic")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "page_id", nullable = false)
    private Page page;

    @Column(name = "language_code", nullable = false, length = 10)
    private String languageCode;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "media_id")
    private UUID mediaId;

    @Column(name = "alt_text", length = 200)
    private String altText;

    // Published live snapshot fields
    @Column(name = "published_title", length = 160)
    private String publishedTitle;

    @Column(name = "published_content", columnDefinition = "TEXT")
    private String publishedContent;

    @Column(name = "published_media_id")
    private UUID publishedMediaId;

    @Column(name = "published_alt_text", length = 200)
    private String publishedAltText;
}
