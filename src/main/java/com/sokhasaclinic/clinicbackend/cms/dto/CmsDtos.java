package com.sokhasaclinic.clinicbackend.cms.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Backward compatibility facade delegating to individual CMS DTO records.
 */
public final class CmsDtos {

    private CmsDtos() {
    }

    public record Image(UUID mediaId, String altText) {
        public Image(ImageDto dto) {
            this(dto.mediaId(), dto.altText());
        }

        public ImageDto toDto() {
            return new ImageDto(mediaId, altText);
        }
    }

    public record Translation(String languageCode, String title, String content, Image image) {
        public Translation(TranslationDto dto) {
            this(dto.languageCode(), dto.title(), dto.content(), dto.image() != null ? new Image(dto.image()) : null);
        }

        public TranslationDto toDto() {
            return new TranslationDto(languageCode, title, content, image != null ? image.toDto() : null);
        }
    }

    public record Save(String slug, List<Translation> translations) {
        public Save(SavePageRequest request) {
            this(request.slug(), request.translations().stream().map(Translation::new).toList());
        }

        public SavePageRequest toRequest() {
            return new SavePageRequest(slug, translations.stream().map(Translation::toDto).toList());
        }
    }

    public record Publish(List<String> languageCodes) {
        public Publish(PublishPageRequest request) {
            this(request.languageCodes());
        }

        public PublishPageRequest toRequest() {
            return new PublishPageRequest(languageCodes);
        }
    }

    public record Detail(
            UUID id,
            String slug,
            String status,
            List<Translation> translations,
            List<String> publishedLanguageCodes,
            Instant publishedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record Title(String languageCode, String title) {
    }

    public record Summary(
            UUID id,
            String slug,
            String status,
            List<Title> translations,
            List<String> publishedLanguageCodes,
            Instant publishedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record SignedImage(String url, String altText) {
    }

    public record PublicPage(
            String slug,
            String status,
            String languageCode,
            String title,
            String content,
            SignedImage image
    ) {
    }

    public record Upload(UUID id, String fileName, String mimeType, long size) {
    }

    public record Data<T>(T data) {
    }

    public record Pagination(
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext,
            boolean hasPrevious
    ) {
    }

    public record Listing(List<Summary> data, Pagination pagination) {
    }

    public record Versioned(Detail data, long revision) {
    }
}
