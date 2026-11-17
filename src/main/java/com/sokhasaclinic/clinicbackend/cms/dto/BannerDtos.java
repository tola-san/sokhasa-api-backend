package com.sokhasaclinic.clinicbackend.cms.dto;

import java.time.Instant;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * Request and response shapes for the homepage banner API.
 */
public final class BannerDtos {

    private BannerDtos() {
    }

    /** Banner text in one language. Used in requests and in the admin response. */
    public record BannerText(String languageCode, String heading, String description, String altText) {
    }

    /**
     * Admin view. {@code translations} holds saved languages in km, en, zh order;
     * {@code missingLanguages} lists the required languages with no saved text.
     */
    public record AdminBanner(
            String imageUrl,
            String fileName,
            Instant publishedAt,
            List<BannerText> translations,
            List<String> missingLanguages) {

        public static AdminBanner empty(List<String> missingLanguages) {
            return new AdminBanner(null, null, null, List.of(), missingLanguages);
        }
    }

    /** Public view: one language, flat. */
    public record PublicBanner(
            String languageCode, String imageUrl, String heading, String description, String altText) {
    }

    /** Admin view plus the revision used for the strong ETag. */
    public record VersionedBanner(AdminBanner data, long revision) {
    }

    /** PATCH parts. A null value means the part was not sent. */
    public record BannerUpdate(MultipartFile image, List<BannerText> translations) {
    }
}
