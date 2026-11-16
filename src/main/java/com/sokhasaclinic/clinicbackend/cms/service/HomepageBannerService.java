package com.sokhasaclinic.clinicbackend.cms.service;

import com.sokhasaclinic.clinicbackend.cms.dto.BannerDtos.AdminBanner;
import com.sokhasaclinic.clinicbackend.cms.dto.BannerDtos.BannerUpdate;
import com.sokhasaclinic.clinicbackend.cms.dto.BannerDtos.PublicBanner;
import com.sokhasaclinic.clinicbackend.cms.dto.BannerDtos.VersionedBanner;
import com.sokhasaclinic.clinicbackend.cms.exception.CmsException;
import com.sokhasaclinic.clinicbackend.cms.model.CmsMedia;
import com.sokhasaclinic.clinicbackend.cms.model.HomepageBanner;
import com.sokhasaclinic.clinicbackend.cms.repository.HomepageBannerRepository;
import com.sokhasaclinic.clinicbackend.cms.repository.MediaRepository;
import com.sokhasaclinic.clinicbackend.cms.service.storage.MediaStorage;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Publishes the homepage hero banner. Saving publishes immediately; the live banner changes
 * only after the image upload and the database update both succeed.
 */
@Service
public class HomepageBannerService {

    static final int ALT_TEXT_MAX = 200;
    static final int HEADING_MAX = 80;
    static final int DESCRIPTION_MAX = 200;
    private static final Pattern ETAG = Pattern.compile("\"(0|[1-9][0-9]{0,17})\"");

    private final HomepageBannerRepository bannerRepository;
    private final MediaRepository mediaRepository;
    private final MediaService mediaService;
    private final MediaCleanupService mediaCleanupService;
    private final MediaStorage mediaStorage;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Autowired
    public HomepageBannerService(
            HomepageBannerRepository bannerRepository,
            MediaRepository mediaRepository,
            MediaService mediaService,
            MediaCleanupService mediaCleanupService,
            MediaStorage mediaStorage,
            TransactionTemplate transactionTemplate) {
        this(bannerRepository, mediaRepository, mediaService, mediaCleanupService, mediaStorage,
                transactionTemplate, Clock.systemUTC());
    }

    HomepageBannerService(
            HomepageBannerRepository bannerRepository,
            MediaRepository mediaRepository,
            MediaService mediaService,
            MediaCleanupService mediaCleanupService,
            MediaStorage mediaStorage,
            TransactionTemplate transactionTemplate,
            Clock clock) {
        this.bannerRepository = bannerRepository;
        this.mediaRepository = mediaRepository;
        this.mediaService = mediaService;
        this.mediaCleanupService = mediaCleanupService;
        this.mediaStorage = mediaStorage;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    public VersionedBanner getAdminBanner() {
        HomepageBanner banner = loadBanner();
        if (!banner.isPublished()) {
            return new VersionedBanner(AdminBanner.empty(), 0);
        }
        CmsMedia media = loadMedia(banner.getMediaId());
        return new VersionedBanner(toAdmin(banner, media), banner.getRevision());
    }

    public PublicBanner getPublicBanner() {
        HomepageBanner banner = loadBanner();
        if (!banner.isPublished()) {
            throw CmsException.bannerNotFound();
        }
        CmsMedia media = loadMedia(banner.getMediaId());
        return new PublicBanner(
                mediaStorage.signedUrl(media.getObjectKey()),
                banner.getAltText(),
                banner.getHeading(),
                banner.getDescription());
    }

    public VersionedBanner publish(String ifMatch, BannerUpdate update) {
        long expectedRevision = parseIfMatch(ifMatch);
        MultipartFile image = update.image() == null || update.image().isEmpty() ? null : update.image();
        String altText = clean("altText", update.altText(), ALT_TEXT_MAX);
        String heading = clean("heading", update.heading(), HEADING_MAX);
        String description = clean("description", update.description(), DESCRIPTION_MAX);

        if (image == null && altText == null && heading == null && description == null) {
            throw CmsException.invalid("Send at least one of image, altText, heading or description.");
        }
        if (image != null && altText == null) {
            throw CmsException.invalid("altText is required with a new image.");
        }

        // Fail fast before uploading; the authoritative check runs again under the row lock.
        HomepageBanner current = loadBanner();
        checkRevision(current, expectedRevision);
        requireCompleteFirstPublish(current, image, altText, heading, description);

        CmsMedia uploaded = image == null ? null : mediaService.store(image);
        Result result;
        try {
            result = transactionTemplate.execute(status -> {
                HomepageBanner banner = bannerRepository.lockSingleton()
                        .orElseThrow(() -> new IllegalStateException("Homepage banner row is missing."));
                checkRevision(banner, expectedRevision);
                requireCompleteFirstPublish(banner, image, altText, heading, description);

                UUID previousMediaId = banner.getMediaId();
                String replacedKey = null;
                if (uploaded != null) {
                    mediaRepository.saveAndFlush(uploaded);
                }
                banner.publish(uploaded == null ? null : uploaded.getId(), altText, heading, description,
                        Instant.now(clock));
                bannerRepository.saveAndFlush(banner);

                if (uploaded != null && previousMediaId != null) {
                    CmsMedia previous = loadMedia(previousMediaId);
                    replacedKey = previous.getObjectKey();
                    mediaRepository.delete(previous);
                    mediaRepository.flush();
                    mediaCleanupService.scheduleNow(replacedKey);
                }

                CmsMedia live = uploaded != null ? uploaded : loadMedia(banner.getMediaId());
                return new Result(banner, live, replacedKey);
            });
        } catch (RuntimeException failure) {
            if (uploaded != null) {
                mediaService.discard(uploaded);
            }
            throw failure;
        }

        if (uploaded != null) {
            mediaCleanupService.complete(uploaded.getObjectKey());
        }
        if (result.replacedKey() != null) {
            mediaService.deleteUnreferenced(result.replacedKey());
        }
        return new VersionedBanner(toAdmin(result.banner(), result.media()), result.banner().getRevision());
    }

    private record Result(HomepageBanner banner, CmsMedia media, String replacedKey) {
    }

    private AdminBanner toAdmin(HomepageBanner banner, CmsMedia media) {
        return new AdminBanner(
                mediaStorage.signedUrl(media.getObjectKey()),
                media.getFileName(),
                banner.getAltText(),
                banner.getHeading(),
                banner.getDescription(),
                banner.getPublishedAt());
    }

    private HomepageBanner loadBanner() {
        return bannerRepository.findSingleton()
                .orElseThrow(() -> new IllegalStateException("Homepage banner row is missing."));
    }

    private CmsMedia loadMedia(UUID mediaId) {
        return mediaRepository.findById(mediaId)
                .orElseThrow(() -> new IllegalStateException("Homepage banner image is missing."));
    }

    private static long parseIfMatch(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw CmsException.versionRequired();
        }
        var matcher = ETAG.matcher(ifMatch.trim());
        if (!matcher.matches()) {
            throw CmsException.invalid("Invalid If-Match.");
        }
        return Long.parseLong(matcher.group(1));
    }

    private static void checkRevision(HomepageBanner banner, long expectedRevision) {
        if (banner.getRevision() != expectedRevision) {
            throw CmsException.versionMismatch();
        }
    }

    private static void requireCompleteFirstPublish(
            HomepageBanner banner, MultipartFile image, String altText, String heading, String description) {
        if (!banner.isPublished() && (image == null || altText == null || heading == null || description == null)) {
            throw CmsException.invalid("The first publish requires image, altText, heading and description.");
        }
    }

    /** Returns null when the part was not sent, otherwise the trimmed, validated value. */
    private static String clean(String field, String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            throw CmsException.invalid(field + " must not be blank.");
        }
        if (trimmed.codePointCount(0, trimmed.length()) > maxLength) {
            throw CmsException.invalid(field + " must be at most " + maxLength + " characters.");
        }
        return trimmed;
    }
}
