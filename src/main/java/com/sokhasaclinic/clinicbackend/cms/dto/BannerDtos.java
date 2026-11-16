package com.sokhasaclinic.clinicbackend.cms.dto;

import java.time.Instant;
import org.springframework.web.multipart.MultipartFile;

/**
 * Request and response shapes for the homepage banner API.
 */
public final class BannerDtos {

    private BannerDtos() {
    }

    /** Admin view. Every field is null before the first publish. */
    public record AdminBanner(
            String imageUrl,
            String fileName,
            String altText,
            String heading,
            String description,
            Instant publishedAt) {

        public static AdminBanner empty() {
            return new AdminBanner(null, null, null, null, null, null);
        }
    }

    public record PublicBanner(String imageUrl, String altText, String heading, String description) {
    }

    /** Admin view plus the revision used for the strong ETag. */
    public record VersionedBanner(AdminBanner data, long revision) {
    }

    /** PATCH parts. A null value means the part was not sent. */
    public record BannerUpdate(MultipartFile image, String altText, String heading, String description) {
    }
}
