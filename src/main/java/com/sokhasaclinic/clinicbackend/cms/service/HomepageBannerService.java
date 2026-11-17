package com.sokhasaclinic.clinicbackend.cms.service;

import com.sokhasaclinic.clinicbackend.cms.dto.BannerDtos.AdminBanner;
import com.sokhasaclinic.clinicbackend.cms.dto.BannerDtos.BannerText;
import com.sokhasaclinic.clinicbackend.cms.dto.BannerDtos.BannerUpdate;
import com.sokhasaclinic.clinicbackend.cms.dto.BannerDtos.PublicBanner;
import com.sokhasaclinic.clinicbackend.cms.dto.BannerDtos.VersionedBanner;
import com.sokhasaclinic.clinicbackend.cms.exception.CmsException;
import com.sokhasaclinic.clinicbackend.cms.model.CmsMedia;
import com.sokhasaclinic.clinicbackend.cms.model.HomepageBanner;
import com.sokhasaclinic.clinicbackend.cms.model.HomepageBannerTranslation;
import com.sokhasaclinic.clinicbackend.cms.repository.HomepageBannerRepository;
import com.sokhasaclinic.clinicbackend.cms.repository.HomepageBannerTranslationRepository;
import com.sokhasaclinic.clinicbackend.cms.repository.MediaRepository;
import com.sokhasaclinic.clinicbackend.cms.service.storage.MediaStorage;
import com.sokhasaclinic.clinicbackend.common.i18n.Language;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Publishes the homepage hero banner. Saving publishes immediately; the live banner changes
 * only after the image upload and the database update both succeed. Every language in
 * {@link Language} must have banner text before anything is published.
 */
@Service
public class HomepageBannerService {

    // Khmer needs more code points than English for the same text, so these limits are per
    // language and larger than the earlier English-only limits (decision I18N-05).
    static final int HEADING_MAX = 120;
    static final int DESCRIPTION_MAX = 300;
    static final int ALT_TEXT_MAX = 200;
    private static final Pattern ETAG = Pattern.compile("\"(0|[1-9][0-9]{0,17})\"");

    private final HomepageBannerRepository bannerRepository;
    private final HomepageBannerTranslationRepository translationRepository;
    private final MediaRepository mediaRepository;
    private final MediaService mediaService;
    private final MediaCleanupService mediaCleanupService;
    private final MediaStorage mediaStorage;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Autowired
    public HomepageBannerService(
            HomepageBannerRepository bannerRepository,
            HomepageBannerTranslationRepository translationRepository,
            MediaRepository mediaRepository,
            MediaService mediaService,
            MediaCleanupService mediaCleanupService,
            MediaStorage mediaStorage,
            TransactionTemplate transactionTemplate) {
        this(bannerRepository, translationRepository, mediaRepository, mediaService, mediaCleanupService,
                mediaStorage, transactionTemplate, Clock.systemUTC());
    }

    HomepageBannerService(
            HomepageBannerRepository bannerRepository,
            HomepageBannerTranslationRepository translationRepository,
            MediaRepository mediaRepository,
            MediaService mediaService,
            MediaCleanupService mediaCleanupService,
            MediaStorage mediaStorage,
            TransactionTemplate transactionTemplate,
            Clock clock) {
        this.bannerRepository = bannerRepository;
        this.translationRepository = translationRepository;
        this.mediaRepository = mediaRepository;
        this.mediaService = mediaService;
        this.mediaCleanupService = mediaCleanupService;
        this.mediaStorage = mediaStorage;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    // Several separate queries. REPEATABLE_READ gives them one PostgreSQL snapshot, so a
    // concurrent publish cannot delete the media row or change a translation between reads.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public VersionedBanner getAdminBanner() {
        HomepageBanner banner = loadBanner();
        Map<Language, HomepageBannerTranslation> saved = savedTranslations();
        if (!banner.isPublished()) {
            return new VersionedBanner(AdminBanner.empty(codes(missing(saved.keySet()))), 0);
        }
        CmsMedia media = loadMedia(banner.getMediaId());
        return new VersionedBanner(toAdmin(banner, media, saved), banner.getRevision());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PublicBanner getPublicBanner(Language language) {
        HomepageBanner banner = loadBanner();
        if (!banner.isPublished()) {
            throw CmsException.bannerNotFound();
        }
        // A language with no saved text is not public (decision I18N-02: no fallback).
        HomepageBannerTranslation text = translationRepository.findById(language.code())
                .orElseThrow(() -> CmsException.bannerNotFound(language));
        CmsMedia media = loadMedia(banner.getMediaId());
        return new PublicBanner(
                language.code(),
                mediaStorage.signedUrl(media.getObjectKey()),
                text.getHeading(),
                text.getDescription(),
                text.getAltText());
    }

    public VersionedBanner publish(String ifMatch, BannerUpdate update) {
        long expectedRevision = parseIfMatch(ifMatch);
        MultipartFile image = update.image() == null || update.image().isEmpty() ? null : update.image();
        Map<Language, BannerText> texts = cleanTranslations(update.translations());

        if (image == null && texts.isEmpty()) {
            throw CmsException.invalid("Send an image, translations, or both.");
        }
        // Every alt text describes the image, so a new image needs all of them at once.
        if (image != null && texts.size() != Language.values().length) {
            throw CmsException.invalid("A new image needs translations for km, en and zh in the same request.");
        }

        // Fail fast before uploading; the authoritative checks run again under the row lock.
        HomepageBanner current = loadBanner();
        checkRevision(current, expectedRevision);
        requirePublishable(current, image, texts, savedTranslations().keySet());

        CmsMedia uploaded = image == null ? null : mediaService.store(image);
        Result result;
        try {
            result = transactionTemplate.execute(status -> {
                HomepageBanner banner = bannerRepository.lockSingleton()
                        .orElseThrow(() -> new IllegalStateException("Homepage banner row is missing."));
                checkRevision(banner, expectedRevision);
                Map<Language, HomepageBannerTranslation> saved = savedTranslations();
                requirePublishable(banner, image, texts, saved.keySet());

                texts.forEach((language, text) -> {
                    HomepageBannerTranslation row = saved.get(language);
                    if (row == null) {
                        row = new HomepageBannerTranslation(language, text.heading(), text.description(),
                                text.altText());
                        saved.put(language, row);
                    } else {
                        row.update(text.heading(), text.description(), text.altText());
                    }
                    translationRepository.save(row);
                });
                translationRepository.flush();

                UUID previousMediaId = banner.getMediaId();
                String replacedKey = null;
                if (uploaded != null) {
                    mediaRepository.saveAndFlush(uploaded);
                }
                banner.publish(uploaded == null ? null : uploaded.getId(), Instant.now(clock));
                bannerRepository.saveAndFlush(banner);

                if (uploaded != null && previousMediaId != null) {
                    CmsMedia previous = loadMedia(previousMediaId);
                    replacedKey = previous.getObjectKey();
                    mediaRepository.delete(previous);
                    mediaRepository.flush();
                    mediaCleanupService.scheduleNow(replacedKey);
                }

                CmsMedia live = uploaded != null ? uploaded : loadMedia(banner.getMediaId());
                return new Result(banner, live, saved, replacedKey);
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
        return new VersionedBanner(toAdmin(result.banner(), result.media(), result.translations()),
                result.banner().getRevision());
    }

    private record Result(
            HomepageBanner banner,
            CmsMedia media,
            Map<Language, HomepageBannerTranslation> translations,
            String replacedKey) {
    }

    private AdminBanner toAdmin(HomepageBanner banner, CmsMedia media,
            Map<Language, HomepageBannerTranslation> saved) {
        List<BannerText> translations = new ArrayList<>();
        // EnumMap iterates in declaration order: km, en, zh.
        saved.forEach((language, row) -> translations.add(
                new BannerText(language.code(), row.getHeading(), row.getDescription(), row.getAltText())));
        return new AdminBanner(
                mediaStorage.signedUrl(media.getObjectKey()),
                media.getFileName(),
                banner.getPublishedAt(),
                List.copyOf(translations),
                codes(missing(saved.keySet())));
    }

    /** Saved rows keyed by language. Rows with an unknown code (impossible under the CHECK) are ignored. */
    private Map<Language, HomepageBannerTranslation> savedTranslations() {
        Map<Language, HomepageBannerTranslation> saved = new EnumMap<>(Language.class);
        for (HomepageBannerTranslation row : translationRepository.findAll()) {
            Language.fromCode(row.getLanguageCode()).ifPresent(language -> saved.put(language, row));
        }
        return saved;
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

    /**
     * The first publish needs an image, and after this request every language must have text
     * (decision I18N-01). Otherwise nothing is saved.
     */
    private static void requirePublishable(HomepageBanner banner, MultipartFile image,
            Map<Language, BannerText> texts, Set<Language> savedLanguages) {
        if (!banner.isPublished() && image == null) {
            throw CmsException.invalid("The first publish requires an image.");
        }
        Set<Language> after = EnumSet.noneOf(Language.class);
        after.addAll(savedLanguages);
        after.addAll(texts.keySet());
        Set<Language> missing = missing(after);
        if (!missing.isEmpty()) {
            throw CmsException.translationMissing(missing);
        }
    }

    private static Set<Language> missing(Set<Language> present) {
        Set<Language> missing = EnumSet.allOf(Language.class);
        missing.removeAll(present);
        return missing;
    }

    private static List<String> codes(Set<Language> languages) {
        return languages.stream().map(Language::code).toList();
    }

    /** Validates and trims each translation. A missing part gives an empty map. */
    private static Map<Language, BannerText> cleanTranslations(List<BannerText> translations) {
        Map<Language, BannerText> cleaned = new EnumMap<>(Language.class);
        if (translations == null) {
            return cleaned;
        }
        for (BannerText text : translations) {
            if (text == null) {
                throw CmsException.invalid("Each translation must be an object.");
            }
            Language language = Language.fromCode(text.languageCode())
                    .orElseThrow(() -> CmsException.invalid("languageCode must be one of km, en, zh."));
            if (cleaned.containsKey(language)) {
                throw CmsException.invalid("languageCode " + language.code() + " appears more than once.");
            }
            String prefix = "translations[" + language.code() + "].";
            cleaned.put(language, new BannerText(
                    language.code(),
                    clean(prefix + "heading", text.heading(), HEADING_MAX),
                    clean(prefix + "description", text.description(), DESCRIPTION_MAX),
                    clean(prefix + "altText", text.altText(), ALT_TEXT_MAX)));
        }
        return cleaned;
    }

    /** Returns the trimmed value; every translation field is required. */
    private static String clean(String field, String value, int maxLength) {
        String trimmed = value == null ? "" : value.strip();
        if (trimmed.isEmpty()) {
            throw CmsException.invalid(field + " must not be blank.");
        }
        if (trimmed.codePointCount(0, trimmed.length()) > maxLength) {
            throw CmsException.invalid(field + " must be at most " + maxLength + " characters.");
        }
        return trimmed;
    }
}
