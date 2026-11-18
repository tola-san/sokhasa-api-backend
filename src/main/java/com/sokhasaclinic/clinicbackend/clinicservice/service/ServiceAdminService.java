package com.sokhasaclinic.clinicbackend.clinicservice.service;

import com.sokhasaclinic.clinicbackend.clinicservice.dto.AdminServiceDtos.AdminService;
import com.sokhasaclinic.clinicbackend.clinicservice.dto.AdminServiceDtos.AdminServiceText;
import com.sokhasaclinic.clinicbackend.clinicservice.dto.AdminServiceDtos.AdminServiceUpdate;
import com.sokhasaclinic.clinicbackend.clinicservice.dto.AdminServiceDtos.ServiceImageAltText;
import com.sokhasaclinic.clinicbackend.clinicservice.dto.AdminServiceDtos.VersionedService;
import com.sokhasaclinic.clinicbackend.clinicservice.model.ClinicService;
import com.sokhasaclinic.clinicbackend.clinicservice.model.ClinicServiceTranslation;
import com.sokhasaclinic.clinicbackend.clinicservice.model.ServiceCategoryTranslation;
import com.sokhasaclinic.clinicbackend.clinicservice.model.ServiceStatus;
import com.sokhasaclinic.clinicbackend.clinicservice.repository.ClinicServiceRepository;
import com.sokhasaclinic.clinicbackend.clinicservice.repository.ClinicServiceTranslationRepository;
import com.sokhasaclinic.clinicbackend.clinicservice.repository.ServiceCategoryTranslationRepository;
import com.sokhasaclinic.clinicbackend.cms.exception.CmsException;
import com.sokhasaclinic.clinicbackend.cms.model.CmsMedia;
import com.sokhasaclinic.clinicbackend.cms.repository.MediaRepository;
import com.sokhasaclinic.clinicbackend.cms.service.MediaCleanupService;
import com.sokhasaclinic.clinicbackend.cms.service.MediaService;
import com.sokhasaclinic.clinicbackend.cms.service.storage.MediaStorage;
import com.sokhasaclinic.clinicbackend.common.i18n.Language;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Admin editing of the seeded services (phase 1, approved November 18, 2026, decisions SA-01 to
 * SA-10): text in each language, duration, buffer, prices, order and active status. Images and their
 * alt text follow the service image decisions (SI-01 to SI-07). Slug and category are not edited (SA-03).
 */
@Service
public class ServiceAdminService {

    private static final Pattern ETAG = Pattern.compile("\"(0|[1-9][0-9]{0,17})\"");
    private static final int NAME_MAX = 120;
    private static final int DESCRIPTION_MAX = 300;
    private static final int ALT_TEXT_MAX = 200;

    private final ClinicServiceRepository serviceRepository;
    private final ClinicServiceTranslationRepository translationRepository;
    private final ServiceCategoryTranslationRepository categoryTranslationRepository;
    private final MediaRepository mediaRepository;
    private final MediaStorage mediaStorage;
    private final MediaService mediaService;
    private final MediaCleanupService mediaCleanupService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Autowired
    public ServiceAdminService(
            ClinicServiceRepository serviceRepository,
            ClinicServiceTranslationRepository translationRepository,
            ServiceCategoryTranslationRepository categoryTranslationRepository,
            MediaRepository mediaRepository,
            MediaStorage mediaStorage,
            MediaService mediaService,
            MediaCleanupService mediaCleanupService,
            TransactionTemplate transactionTemplate) {
        this(serviceRepository, translationRepository, categoryTranslationRepository, mediaRepository,
                mediaStorage, mediaService, mediaCleanupService, transactionTemplate, Clock.systemUTC());
    }

    ServiceAdminService(
            ClinicServiceRepository serviceRepository,
            ClinicServiceTranslationRepository translationRepository,
            ServiceCategoryTranslationRepository categoryTranslationRepository,
            MediaRepository mediaRepository,
            MediaStorage mediaStorage,
            MediaService mediaService,
            MediaCleanupService mediaCleanupService,
            TransactionTemplate transactionTemplate,
            Clock clock) {
        this.serviceRepository = serviceRepository;
        this.translationRepository = translationRepository;
        this.categoryTranslationRepository = categoryTranslationRepository;
        this.mediaRepository = mediaRepository;
        this.mediaStorage = mediaStorage;
        this.mediaService = mediaService;
        this.mediaCleanupService = mediaCleanupService;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    /** Every service, active and inactive, in public list order. */
    @Transactional(readOnly = true)
    public List<AdminService> listServices() {
        List<ClinicService> services = serviceRepository.findAllForAdmin();
        if (services.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<ClinicServiceTranslation>> texts = translationRepository
                .findByServiceIds(services.stream().map(ClinicService::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(text -> text.getId().getOwnerId()));
        Map<UUID, String> categoryNames = categoryNames(services);
        Map<UUID, CmsMedia> images = images(services);
        return services.stream()
                .map(service -> toResponse(service, texts.getOrDefault(service.getId(), List.of()),
                        categoryNames, images))
                .toList();
    }

    @Transactional(readOnly = true)
    public VersionedService getService(String id) {
        ClinicService service = serviceRepository.findForAdmin(parseId(id)).orElseThrow(CmsException::serviceNotFound);
        return versioned(service);
    }

    @Transactional
    public VersionedService updateService(String id, String ifMatch, AdminServiceUpdate update) {
        long expectedVersion = parseIfMatch(ifMatch);
        ClinicService service = loadForUpdate(id, expectedVersion);

        checkPrices(update.priceUsd(), update.originalPriceUsd());
        Map<Language, AdminServiceText> sent = cleanTranslations(update.translations());

        Map<Language, ClinicServiceTranslation> saved = savedTranslations(service.getId());
        if (service.getStatus() == ServiceStatus.ACTIVE) {
            Set<Language> after = EnumSet.noneOf(Language.class);
            after.addAll(saved.keySet());
            after.addAll(sent.keySet());
            requireAllLanguages(after);
        }

        // Alt text describes the image, so it can only be sent while there is one (SI-06).
        if (service.getImageMediaId() == null && sent.values().stream().anyMatch(text -> text.imageAltText() != null)) {
            throw CmsException.invalid("imageAltText cannot be set until the service has an image.");
        }

        sent.forEach((language, text) -> {
            ClinicServiceTranslation row = saved.get(language);
            if (row == null) {
                // A service with an image already has every language (SI-04), so a new row has no image yet.
                saved.put(language, translationRepository.save(
                        new ClinicServiceTranslation(service.getId(), language, text.name(), text.shortDescription())));
            } else {
                row.update(text.name(), text.shortDescription());
                if (text.imageAltText() != null) {
                    row.changeImageAltText(text.imageAltText());
                }
            }
        });
        service.updateDetails(update.durationMinutes(), update.bufferMinutes(), scaled(update.priceUsd()),
                scaled(update.originalPriceUsd()), update.sortOrder(), Instant.now(clock));
        serviceRepository.saveAndFlush(service);
        return versioned(service, saved);
    }

    /** Activation needs every language (SA-06). Setting the current status again changes nothing. */
    @Transactional
    public VersionedService changeStatus(String id, String ifMatch, ServiceStatus status) {
        long expectedVersion = parseIfMatch(ifMatch);
        ClinicService service = loadForUpdate(id, expectedVersion);
        Map<Language, ClinicServiceTranslation> saved = savedTranslations(service.getId());
        if (status == ServiceStatus.ACTIVE) {
            requireAllLanguages(saved.keySet());
        }
        if (service.changeStatus(status, Instant.now(clock))) {
            serviceRepository.saveAndFlush(service);
        }
        return versioned(service, saved);
    }

    /**
     * Sets or replaces the image with alt text in every language (SI-03, SI-04). The file is
     * uploaded first; the service switches to it only if the database update then succeeds, and the
     * replaced file is deleted from storage after the commit (SI-05).
     */
    public VersionedService replaceImage(String id, String ifMatch, MultipartFile image,
            List<ServiceImageAltText> altTexts) {
        long expectedVersion = parseIfMatch(ifMatch);
        UUID serviceId = parseId(id);
        Map<Language, String> alts = cleanAltTexts(altTexts);
        if (image == null || image.isEmpty()) {
            throw CmsException.invalid("An image is required.");
        }

        // Fail fast before uploading; the authoritative checks run again under the row lock.
        transactionTemplate.executeWithoutResult(status -> requireImageable(
                loadForUpdate(serviceId, expectedVersion)));

        CmsMedia uploaded = mediaService.store(image);
        ImageChange change;
        try {
            change = transactionTemplate.execute(status -> {
                ClinicService service = loadForUpdate(serviceId, expectedVersion);
                Map<Language, ClinicServiceTranslation> saved = requireImageable(service);
                mediaRepository.saveAndFlush(uploaded);
                UUID previousId = service.getImageMediaId();
                alts.forEach((language, alt) -> saved.get(language).changeImageAltText(alt));
                service.changeImage(uploaded.getId(), Instant.now(clock));
                serviceRepository.saveAndFlush(service);
                String replacedKey = removeMedia(previousId);
                return new ImageChange(versioned(service, saved), replacedKey);
            });
        } catch (RuntimeException failure) {
            mediaService.discard(uploaded);
            throw failure;
        }

        mediaCleanupService.complete(uploaded.getObjectKey());
        if (change.replacedKey() != null) {
            mediaService.deleteUnreferenced(change.replacedKey());
        }
        return change.service();
    }

    /** Removes the image and every alt text (SI-05). Removing when there is none changes nothing. */
    public VersionedService removeImage(String id, String ifMatch) {
        long expectedVersion = parseIfMatch(ifMatch);
        UUID serviceId = parseId(id);
        ImageChange change = transactionTemplate.execute(status -> {
            ClinicService service = loadForUpdate(serviceId, expectedVersion);
            Map<Language, ClinicServiceTranslation> saved = savedTranslations(service.getId());
            if (service.getImageMediaId() == null) {
                return new ImageChange(versioned(service, saved), null);
            }
            UUID previousId = service.getImageMediaId();
            saved.values().forEach(row -> row.changeImageAltText(null));
            service.changeImage(null, Instant.now(clock));
            serviceRepository.saveAndFlush(service);
            return new ImageChange(versioned(service, saved), removeMedia(previousId));
        });
        if (change.replacedKey() != null) {
            mediaService.deleteUnreferenced(change.replacedKey());
        }
        return change.service();
    }

    private record ImageChange(VersionedService service, String replacedKey) {
    }

    /** Every language needs its text before an image can carry alt text in it (SI-04). */
    private Map<Language, ClinicServiceTranslation> requireImageable(ClinicService service) {
        Map<Language, ClinicServiceTranslation> saved = savedTranslations(service.getId());
        requireAllLanguages(saved.keySet());
        return saved;
    }

    /**
     * Deletes the media row of a replaced image and schedules its object for deletion. Must run
     * after the service stopped pointing at it. Returns the object key, or null when there was none.
     */
    private String removeMedia(UUID mediaId) {
        if (mediaId == null) {
            return null;
        }
        CmsMedia previous = mediaRepository.findById(mediaId).orElse(null);
        if (previous == null) {
            return null;
        }
        mediaRepository.delete(previous);
        mediaRepository.flush();
        mediaCleanupService.scheduleNow(previous.getObjectKey());
        return previous.getObjectKey();
    }

    /** Exactly one alt text per language, trimmed, 1–200 characters (SI-03). */
    private static Map<Language, String> cleanAltTexts(List<ServiceImageAltText> altTexts) {
        Map<Language, String> cleaned = new EnumMap<>(Language.class);
        if (altTexts != null) {
            for (ServiceImageAltText alt : altTexts) {
                if (alt == null) {
                    throw CmsException.invalid("altTexts must not contain null entries.");
                }
                Language language = Language.fromCode(alt.languageCode())
                        .orElseThrow(() -> CmsException.invalid("languageCode must be one of km, en, zh."));
                if (cleaned.containsKey(language)) {
                    throw CmsException.invalid("Each languageCode may appear only once.");
                }
                cleaned.put(language, trimmed(alt.imageAltText(), ALT_TEXT_MAX, "imageAltText", language));
            }
        }
        if (cleaned.size() != Language.values().length) {
            throw CmsException.invalid("A new image needs alt text in km, en and zh.");
        }
        return cleaned;
    }

    private ClinicService loadForUpdate(UUID id, long expectedVersion) {
        ClinicService service = serviceRepository.findForUpdate(id).orElseThrow(CmsException::serviceNotFound);
        if (service.getVersion() != expectedVersion) {
            throw CmsException.serviceVersionMismatch();
        }
        return service;
    }

    private ClinicService loadForUpdate(String id, long expectedVersion) {
        ClinicService service = serviceRepository.findForUpdate(parseId(id)).orElseThrow(CmsException::serviceNotFound);
        if (service.getVersion() != expectedVersion) {
            throw CmsException.serviceVersionMismatch();
        }
        return service;
    }

    private static UUID parseId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw CmsException.serviceNotFound();
        }
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

    /** A promotion price needs a current price and must be higher than it. */
    private static void checkPrices(BigDecimal price, BigDecimal originalPrice) {
        if (originalPrice != null && (price == null || originalPrice.compareTo(price) <= 0)) {
            throw CmsException.invalid("originalPriceUsd must be greater than priceUsd.");
        }
    }

    private static BigDecimal scaled(BigDecimal amount) {
        return amount == null ? null : amount.setScale(2);
    }

    /** Trims and checks the sent languages; each code at most once (SA-05). */
    private static Map<Language, AdminServiceText> cleanTranslations(List<AdminServiceText> translations) {
        Map<Language, AdminServiceText> cleaned = new EnumMap<>(Language.class);
        if (translations == null) {
            return cleaned;
        }
        for (AdminServiceText text : translations) {
            Language language = Language.fromCode(text.languageCode())
                    .orElseThrow(() -> CmsException.invalid("languageCode must be one of km, en, zh."));
            if (cleaned.containsKey(language)) {
                throw CmsException.invalid("Each languageCode may appear only once.");
            }
            String name = trimmed(text.name(), NAME_MAX, "name", language);
            String description = trimmed(text.shortDescription(), DESCRIPTION_MAX, "shortDescription", language);
            // Null keeps the saved alt text; whether one may be sent is checked against the image (SI-06).
            String alt = text.imageAltText() == null
                    ? null
                    : trimmed(text.imageAltText(), ALT_TEXT_MAX, "imageAltText", language);
            cleaned.put(language, new AdminServiceText(language.code(), name, description, alt));
        }
        return cleaned;
    }

    private static String trimmed(String value, int max, String field, Language language) {
        String text = value == null ? "" : value.strip();
        int length = text.codePointCount(0, text.length());
        if (length < 1 || length > max) {
            throw CmsException.invalid("translations[" + language.code() + "]." + field
                    + " must be 1-" + max + " characters.");
        }
        return text;
    }

    private static void requireAllLanguages(Set<Language> present) {
        List<Language> missing = missing(present);
        if (!missing.isEmpty()) {
            throw CmsException.serviceTranslationMissing(missing);
        }
    }

    private static List<Language> missing(Set<Language> present) {
        List<Language> missing = new ArrayList<>();
        for (Language language : Language.values()) {
            if (!present.contains(language)) {
                missing.add(language);
            }
        }
        return missing;
    }

    private Map<Language, ClinicServiceTranslation> savedTranslations(UUID serviceId) {
        return byLanguage(translationRepository.findByServiceIds(List.of(serviceId)));
    }

    private static Map<Language, ClinicServiceTranslation> byLanguage(List<ClinicServiceTranslation> rows) {
        Map<Language, ClinicServiceTranslation> saved = new EnumMap<>(Language.class);
        for (ClinicServiceTranslation row : rows) {
            Language.fromCode(row.getId().getLanguageCode()).ifPresent(language -> saved.put(language, row));
        }
        return saved;
    }

    private VersionedService versioned(ClinicService service) {
        return versioned(service, savedTranslations(service.getId()));
    }

    private VersionedService versioned(ClinicService service, Map<Language, ClinicServiceTranslation> saved) {
        AdminService data = toResponse(service, List.copyOf(saved.values()), categoryNames(List.of(service)),
                images(List.of(service)));
        return new VersionedService(data, service.getVersion());
    }

    private Map<UUID, String> categoryNames(List<ClinicService> services) {
        Set<UUID> categoryIds = services.stream().map(service -> service.getCategory().getId())
                .collect(Collectors.toSet());
        return categoryTranslationRepository.findNames(categoryIds, Language.EN.code()).stream()
                .collect(Collectors.toMap(row -> row.getId().getOwnerId(), ServiceCategoryTranslation::getName));
    }

    private Map<UUID, CmsMedia> images(List<ClinicService> services) {
        Set<UUID> mediaIds = services.stream().map(ClinicService::getImageMediaId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (mediaIds.isEmpty()) {
            return Map.of();
        }
        return mediaRepository.findAllById(mediaIds).stream()
                .collect(Collectors.toMap(CmsMedia::getId, Function.identity()));
    }

    private AdminService toResponse(ClinicService service, List<ClinicServiceTranslation> rows,
            Map<UUID, String> categoryNames, Map<UUID, CmsMedia> images) {
        Map<Language, ClinicServiceTranslation> saved = byLanguage(rows);
        // EnumMap iterates in declaration order: km, en, zh.
        List<AdminServiceText> translations = saved.entrySet().stream()
                .map(entry -> new AdminServiceText(entry.getKey().code(), entry.getValue().getName(),
                        entry.getValue().getShortDescription(), entry.getValue().getImageAltText()))
                .toList();
        CmsMedia image = service.getImageMediaId() == null ? null : images.get(service.getImageMediaId());
        return new AdminService(
                service.getId(),
                service.getSlug(),
                service.getCategory().getSlug(),
                categoryNames.getOrDefault(service.getCategory().getId(), service.getCategory().getSlug()),
                service.getStatus().name(),
                service.getDurationMinutes(),
                service.getBufferMinutes(),
                service.getPriceUsd(),
                service.getOriginalPriceUsd(),
                service.getSortOrder(),
                image == null ? null : mediaStorage.signedUrl(image.getObjectKey()),
                service.getUpdatedAt(),
                service.getVersion(),
                translations,
                missing(saved.keySet()).stream().map(Language::code).toList());
    }
}
