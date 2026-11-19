package com.sokhasaclinic.clinicbackend.doctor.service;

import com.sokhasaclinic.clinicbackend.clinicservice.model.ClinicService;
import com.sokhasaclinic.clinicbackend.clinicservice.repository.ClinicServiceRepository;
import com.sokhasaclinic.clinicbackend.cms.exception.CmsException;
import com.sokhasaclinic.clinicbackend.cms.model.CmsMedia;
import com.sokhasaclinic.clinicbackend.cms.repository.MediaRepository;
import com.sokhasaclinic.clinicbackend.cms.service.MediaCleanupService;
import com.sokhasaclinic.clinicbackend.cms.service.MediaService;
import com.sokhasaclinic.clinicbackend.cms.service.storage.MediaStorage;
import com.sokhasaclinic.clinicbackend.common.i18n.Language;
import com.sokhasaclinic.clinicbackend.common.time.ClinicTime;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorProfileDtos.ImageAltText;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorProfileDtos.Profile;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorProfileDtos.ProfileText;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorProfileDtos.ProfileWrite;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorProfileDtos.PublicDoctor;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorProfileDtos.PublicDoctorDetail;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorProfileDtos.PublicDoctorService;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorProfileDtos.ServiceDoctors;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorProfileDtos.ServiceDoctorsWrite;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorProfileDtos.Versioned;
import com.sokhasaclinic.clinicbackend.doctor.model.Doctor;
import com.sokhasaclinic.clinicbackend.doctor.model.DoctorStatus;
import com.sokhasaclinic.clinicbackend.doctor.model.DoctorTranslation;
import com.sokhasaclinic.clinicbackend.doctor.repository.DoctorRepository;
import com.sokhasaclinic.clinicbackend.doctor.repository.DoctorTranslationRepository;
import com.sokhasaclinic.clinicbackend.doctor.repository.ServiceDoctorLinks;
import com.sokhasaclinic.clinicbackend.doctor.repository.ServiceDoctorLinks.LinkedService;
import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
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
 * Public doctor profiles (PC-04 to PC-08) with card details and photos (DP-01 to DP-11). Photos
 * follow the service image flow: upload first, switch inside a locked transaction, delete the
 * replaced object after the commit (SI-05).
 */
@Service
public class DoctorProfileService {

    private static final Pattern ETAG = Pattern.compile("\"(0|[1-9][0-9]{0,17})\"");
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final int SLUG_MAX = 80;
    private static final int TITLE_MAX = 120;
    private static final int BIO_MAX = 3000;
    private static final int EDUCATION_MAX = 500;
    private static final int TAG_MAX = 40;
    private static final int TAGS_MAX = 5;
    private static final int ALT_TEXT_MAX = 200;
    private static final int FIRST_PRACTICE_YEAR = 1950;
    private static final int PUBLICATIONS_MAX = 10_000;
    private static final int LINKED_DOCTORS_MAX = 50;

    private final DoctorRepository doctors;
    private final DoctorTranslationRepository translations;
    private final ServiceDoctorLinks links;
    private final ClinicServiceRepository services;
    private final MediaRepository mediaRepository;
    private final MediaStorage mediaStorage;
    private final MediaService mediaService;
    private final MediaCleanupService mediaCleanupService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Autowired
    public DoctorProfileService(DoctorRepository doctors, DoctorTranslationRepository translations,
            ServiceDoctorLinks links, ClinicServiceRepository services, MediaRepository mediaRepository,
            MediaStorage mediaStorage, MediaService mediaService, MediaCleanupService mediaCleanupService,
            TransactionTemplate transactionTemplate) {
        this(doctors, translations, links, services, mediaRepository, mediaStorage, mediaService,
                mediaCleanupService, transactionTemplate, Clock.systemUTC());
    }

    DoctorProfileService(DoctorRepository doctors, DoctorTranslationRepository translations,
            ServiceDoctorLinks links, ClinicServiceRepository services, MediaRepository mediaRepository,
            MediaStorage mediaStorage, MediaService mediaService, MediaCleanupService mediaCleanupService,
            TransactionTemplate transactionTemplate, Clock clock) {
        this.doctors = doctors;
        this.translations = translations;
        this.links = links;
        this.services = services;
        this.mediaRepository = mediaRepository;
        this.mediaStorage = mediaStorage;
        this.mediaService = mediaService;
        this.mediaCleanupService = mediaCleanupService;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    // ---- Public ---------------------------------------------------------------------------------

    /** PC-04, DP-09: active doctors with a slug and a profile in every language, with every card field. */
    @Transactional(readOnly = true)
    public List<PublicDoctor> publicDoctors(Language language) {
        List<Doctor> candidates = doctors.findByStatusAndSlugIsNotNullOrderBySortOrderAscFullNameAscIdAsc(
                DoctorStatus.ACTIVE);
        Map<UUID, Map<Language, DoctorTranslation>> texts = texts(candidates.stream().map(Doctor::getId).toList());
        List<Doctor> visible = candidates.stream()
                .filter(doctor -> complete(texts.get(doctor.getId())))
                .toList();
        Map<UUID, CmsMedia> images = images(visible);
        Map<UUID, List<LinkedService>> linked = links.activeServices(
                visible.stream().map(Doctor::getId).toList(), language.code());
        return visible.stream()
                .map(doctor -> publicDoctor(doctor, texts.get(doctor.getId()).get(language), images,
                        linked.getOrDefault(doctor.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PublicDoctorDetail publicDoctor(String slug, Language language) {
        Doctor doctor = doctors.findBySlugAndStatus(slug, DoctorStatus.ACTIVE).orElseThrow(CmsException::doctorNotFound);
        Map<Language, DoctorTranslation> text = texts(List.of(doctor.getId())).get(doctor.getId());
        if (!complete(text)) {
            throw CmsException.doctorNotFound();
        }
        List<LinkedService> linked = links.activeServices(List.of(doctor.getId()), language.code())
                .getOrDefault(doctor.getId(), List.of());
        PublicDoctor item = publicDoctor(doctor, text.get(language), images(List.of(doctor)), linked);
        return new PublicDoctorDetail(item.slug(), item.fullName(), item.title(), item.bio(), item.education(),
                item.tags(), item.experienceYears(), item.publicationsCount(), item.imageUrl(), item.imageAltText(),
                item.serviceSlugs(),
                linked.stream().map(s -> new PublicDoctorService(s.slug(), s.name(), s.shortDescription())).toList());
    }

    // ---- Admin profile --------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Versioned<Profile> profile(String id) {
        return versioned(doctors.findById(parseDoctorId(id)).orElseThrow(CmsException::doctorNotFound));
    }

    @Transactional
    public Versioned<Profile> updateProfile(String id, String ifMatch, ProfileWrite write) {
        long expectedVersion = parseIfMatch(ifMatch);
        Doctor doctor = loadForUpdate(parseDoctorId(id), expectedVersion);
        if (write == null) {
            throw CmsException.invalid("A profile body is required.");
        }
        String slug = cleanSlug(write.slug());
        Integer startYear = checkStartYear(write.practiceStartYear());
        Integer publications = checkPublications(write.publicationsCount());
        Map<Language, ProfileText> sent = cleanTexts(write.translations());
        if (doctor.getImageMediaId() == null && sent.values().stream().anyMatch(text -> text.imageAltText() != null)) {
            throw CmsException.invalid("imageAltText cannot be set until the doctor has a photo.");
        }
        if (doctors.existsBySlugAndIdNot(slug, doctor.getId())) {
            throw CmsException.doctorSlugTaken();
        }

        Map<Language, DoctorTranslation> saved = savedTexts(doctor.getId());
        sent.forEach((language, text) -> {
            DoctorTranslation row = saved.get(language);
            if (row == null) {
                // A doctor with a photo already has every language (DP-07), so a new row has no alt text.
                saved.put(language, translations.save(new DoctorTranslation(doctor.getId(), language, text.title(),
                        text.bio(), text.education(), text.tags())));
            } else {
                row.update(text.title(), text.bio(), text.education(), text.tags());
                if (text.imageAltText() != null) {
                    row.changeImageAltText(text.imageAltText());
                }
            }
        });
        doctor.updateProfile(slug, startYear, publications, Instant.now(clock));
        doctors.saveAndFlush(doctor);
        return versioned(doctor, saved);
    }

    // ---- Admin photo ----------------------------------------------------------------------------

    /** DP-06, DP-07: sets or replaces the photo with alt text in every language. */
    public Versioned<Profile> replaceImage(String id, String ifMatch, MultipartFile image, List<ImageAltText> altTexts) {
        long expectedVersion = parseIfMatch(ifMatch);
        UUID doctorId = parseDoctorId(id);
        Map<Language, String> alts = cleanAltTexts(altTexts);
        if (image == null || image.isEmpty()) {
            throw CmsException.invalid("An image is required.");
        }

        // Fail fast before uploading; the authoritative checks run again under the row lock.
        transactionTemplate.executeWithoutResult(status -> requireImageable(loadForUpdate(doctorId, expectedVersion)));

        CmsMedia uploaded = mediaService.store(image);
        ImageChange change;
        try {
            change = transactionTemplate.execute(status -> {
                Doctor doctor = loadForUpdate(doctorId, expectedVersion);
                Map<Language, DoctorTranslation> saved = requireImageable(doctor);
                mediaRepository.saveAndFlush(uploaded);
                UUID previousId = doctor.getImageMediaId();
                alts.forEach((language, alt) -> saved.get(language).changeImageAltText(alt));
                doctor.changeImage(uploaded.getId(), Instant.now(clock));
                doctors.saveAndFlush(doctor);
                String replacedKey = removeMedia(previousId);
                return new ImageChange(versioned(doctor, saved), replacedKey);
            });
        } catch (RuntimeException failure) {
            mediaService.discard(uploaded);
            throw failure;
        }

        mediaCleanupService.complete(uploaded.getObjectKey());
        if (change.replacedKey() != null) {
            mediaService.deleteUnreferenced(change.replacedKey());
        }
        return change.profile();
    }

    /** Removes the photo and every alt text. Removing when there is none changes nothing. */
    public Versioned<Profile> removeImage(String id, String ifMatch) {
        long expectedVersion = parseIfMatch(ifMatch);
        UUID doctorId = parseDoctorId(id);
        ImageChange change = transactionTemplate.execute(status -> {
            Doctor doctor = loadForUpdate(doctorId, expectedVersion);
            Map<Language, DoctorTranslation> saved = savedTexts(doctor.getId());
            if (doctor.getImageMediaId() == null) {
                return new ImageChange(versioned(doctor, saved), null);
            }
            UUID previousId = doctor.getImageMediaId();
            saved.values().forEach(row -> row.changeImageAltText(null));
            doctor.changeImage(null, Instant.now(clock));
            doctors.saveAndFlush(doctor);
            return new ImageChange(versioned(doctor, saved), removeMedia(previousId));
        });
        if (change.replacedKey() != null) {
            mediaService.deleteUnreferenced(change.replacedKey());
        }
        return change.profile();
    }

    private record ImageChange(Versioned<Profile> profile, String replacedKey) {
    }

    // ---- Service links --------------------------------------------------------------------------

    /** PC-06: replaces the service's linked doctors; the service's version increases. */
    @Transactional
    public Versioned<ServiceDoctors> replaceServiceDoctors(String serviceId, String ifMatch, ServiceDoctorsWrite write) {
        long expectedVersion = parseIfMatch(ifMatch);
        ClinicService service = services.findForUpdate(parseServiceId(serviceId)).orElseThrow(CmsException::serviceNotFound);
        if (service.getVersion() != expectedVersion) {
            throw CmsException.serviceVersionMismatch();
        }
        if (write == null || write.doctorIds() == null) {
            throw CmsException.invalid("doctorIds is required.");
        }
        if (write.doctorIds().size() > LINKED_DOCTORS_MAX) {
            throw CmsException.invalid("A service may link at most " + LINKED_DOCTORS_MAX + " doctors.");
        }
        Set<UUID> doctorIds = new LinkedHashSet<>();
        for (UUID doctorId : write.doctorIds()) {
            if (doctorId == null || !doctorIds.add(doctorId)) {
                throw CmsException.invalid("doctorIds must be distinct doctor ids.");
            }
        }
        if (doctors.findAllById(doctorIds).size() != doctorIds.size()) {
            throw CmsException.doctorNotFound();
        }
        links.replace(service.getId(), doctorIds);
        service.touch(Instant.now(clock));
        services.saveAndFlush(service);
        return new Versioned<>(new ServiceDoctors(service.getId(), links.doctorIds(service.getId())), service.getVersion());
    }

    // ---- Validation -----------------------------------------------------------------------------

    private static String cleanSlug(String value) {
        String slug = value == null ? "" : value.strip();
        if (slug.isEmpty() || slug.length() > SLUG_MAX || !SLUG.matcher(slug).matches()) {
            throw CmsException.invalid("slug must be 1-80 lowercase letters, digits and single hyphens.");
        }
        return slug;
    }

    private Integer checkStartYear(Integer year) {
        int currentYear = Year.now(clock.withZone(ClinicTime.ZONE)).getValue();
        if (year != null && (year < FIRST_PRACTICE_YEAR || year > currentYear)) {
            throw CmsException.invalid("practiceStartYear must be from " + FIRST_PRACTICE_YEAR + " to " + currentYear + ".");
        }
        return year;
    }

    private static Integer checkPublications(Integer count) {
        if (count != null && (count < 0 || count > PUBLICATIONS_MAX)) {
            throw CmsException.invalid("publicationsCount must be from 0 to " + PUBLICATIONS_MAX + ".");
        }
        return count;
    }

    /** Exactly one entry per language, trimmed and checked (PC-05, DP-01, DP-02, DP-08). */
    private static Map<Language, ProfileText> cleanTexts(List<ProfileText> sent) {
        Map<Language, ProfileText> cleaned = new EnumMap<>(Language.class);
        if (sent != null) {
            for (ProfileText text : sent) {
                if (text == null) {
                    throw CmsException.invalid("translations must not contain null entries.");
                }
                Language language = Language.fromCode(text.languageCode())
                        .orElseThrow(() -> CmsException.invalid("languageCode must be one of km, en, zh."));
                if (cleaned.containsKey(language)) {
                    throw CmsException.invalid("Each languageCode may appear only once.");
                }
                cleaned.put(language, new ProfileText(language.code(),
                        required(text.title(), TITLE_MAX, "title", language),
                        optional(text.bio(), BIO_MAX, "bio", language),
                        optional(text.education(), EDUCATION_MAX, "education", language),
                        cleanTags(text.tags(), language),
                        text.imageAltText() == null ? null
                                : required(text.imageAltText(), ALT_TEXT_MAX, "imageAltText", language)));
            }
        }
        if (cleaned.size() != Language.values().length) {
            throw CmsException.invalid("translations needs exactly one entry each for km, en and zh.");
        }
        return cleaned;
    }

    private static List<String> cleanTags(List<String> tags, Language language) {
        if (tags == null) {
            return List.of();
        }
        if (tags.size() > TAGS_MAX) {
            throw CmsException.invalid("translations[" + language.code() + "].tags allows at most " + TAGS_MAX + " tags.");
        }
        Set<String> cleaned = new LinkedHashSet<>();
        for (String tag : tags) {
            if (!cleaned.add(required(tag, TAG_MAX, "tags", language))) {
                throw CmsException.invalid("translations[" + language.code() + "].tags must not repeat a tag.");
            }
        }
        return List.copyOf(cleaned);
    }

    /** Exactly one alt text per language, trimmed, 1–200 characters (DP-06). */
    private static Map<Language, String> cleanAltTexts(List<ImageAltText> altTexts) {
        Map<Language, String> cleaned = new EnumMap<>(Language.class);
        if (altTexts != null) {
            for (ImageAltText alt : altTexts) {
                if (alt == null) {
                    throw CmsException.invalid("altTexts must not contain null entries.");
                }
                Language language = Language.fromCode(alt.languageCode())
                        .orElseThrow(() -> CmsException.invalid("languageCode must be one of km, en, zh."));
                if (cleaned.containsKey(language)) {
                    throw CmsException.invalid("Each languageCode may appear only once.");
                }
                cleaned.put(language, required(alt.imageAltText(), ALT_TEXT_MAX, "imageAltText", language));
            }
        }
        if (cleaned.size() != Language.values().length) {
            throw CmsException.invalid("A new photo needs alt text in km, en and zh.");
        }
        return cleaned;
    }

    private static String required(String value, int max, String field, Language language) {
        String text = value == null ? "" : value.strip();
        int length = text.codePointCount(0, text.length());
        if (length < 1 || length > max) {
            throw CmsException.invalid("translations[" + language.code() + "]." + field
                    + " must be 1-" + max + " characters.");
        }
        return text;
    }

    /** Blank counts as not set. */
    private static String optional(String value, int max, String field, Language language) {
        return value == null || value.isBlank() ? null : required(value, max, field, language);
    }

    // ---- Loading --------------------------------------------------------------------------------

    private Doctor loadForUpdate(UUID id, long expectedVersion) {
        Doctor doctor = doctors.findForUpdate(id).orElseThrow(CmsException::doctorNotFound);
        if (doctor.getVersion() != expectedVersion) {
            throw CmsException.doctorVersionMismatch();
        }
        return doctor;
    }

    private Map<Language, DoctorTranslation> requireImageable(Doctor doctor) {
        Map<Language, DoctorTranslation> saved = savedTexts(doctor.getId());
        List<Language> missing = missing(saved.keySet());
        if (!missing.isEmpty()) {
            throw CmsException.doctorTranslationMissing(missing);
        }
        return saved;
    }

    /** Deletes the media row of a replaced photo and schedules its object for deletion. */
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

    private Map<Language, DoctorTranslation> savedTexts(UUID doctorId) {
        Map<Language, DoctorTranslation> saved = texts(List.of(doctorId)).get(doctorId);
        return saved == null ? new EnumMap<>(Language.class) : saved;
    }

    private Map<UUID, Map<Language, DoctorTranslation>> texts(Collection<UUID> doctorIds) {
        if (doctorIds.isEmpty()) {
            return Map.of();
        }
        return translations.findByDoctorIds(doctorIds).stream()
                .filter(row -> Language.fromCode(row.getId().getLanguageCode()).isPresent())
                .collect(Collectors.groupingBy(row -> row.getId().getOwnerId(),
                        Collectors.toMap(row -> Language.fromCode(row.getId().getLanguageCode()).orElseThrow(),
                                Function.identity(), (a, b) -> a, () -> new EnumMap<>(Language.class))));
    }

    private Map<UUID, CmsMedia> images(List<Doctor> list) {
        Set<UUID> mediaIds = list.stream().map(Doctor::getImageMediaId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (mediaIds.isEmpty()) {
            return Map.of();
        }
        return mediaRepository.findAllById(mediaIds).stream()
                .collect(Collectors.toMap(CmsMedia::getId, Function.identity()));
    }

    private String imageUrl(Doctor doctor, Map<UUID, CmsMedia> images) {
        CmsMedia image = doctor.getImageMediaId() == null ? null : images.get(doctor.getImageMediaId());
        return image == null ? null : mediaStorage.signedUrl(image.getObjectKey());
    }

    // ---- Responses ------------------------------------------------------------------------------

    private PublicDoctor publicDoctor(Doctor doctor, DoctorTranslation text, Map<UUID, CmsMedia> images,
            List<LinkedService> linked) {
        String imageUrl = imageUrl(doctor, images);
        return new PublicDoctor(doctor.getSlug(), doctor.getFullName(), text.getTitle(), text.getBio(),
                text.getEducation(), text.getTags(), experienceYears(doctor.getPracticeStartYear()),
                doctor.getPublicationsCount(), imageUrl, imageUrl == null ? null : text.getImageAltText(),
                linked.stream().map(LinkedService::slug).toList());
    }

    /** DP-03: derived from the start year, in clinic time, so it never goes stale. */
    private Integer experienceYears(Integer startYear) {
        if (startYear == null) {
            return null;
        }
        return Math.max(0, Year.now(clock.withZone(ClinicTime.ZONE)).getValue() - startYear);
    }

    private Versioned<Profile> versioned(Doctor doctor) {
        return versioned(doctor, savedTexts(doctor.getId()));
    }

    private Versioned<Profile> versioned(Doctor doctor, Map<Language, DoctorTranslation> saved) {
        // EnumMap iterates in declaration order: km, en, zh.
        List<ProfileText> texts = new ArrayList<>();
        new EnumMap<>(saved).forEach((language, row) -> texts.add(new ProfileText(language.code(), row.getTitle(),
                row.getBio(), row.getEducation(), row.getTags(), row.getImageAltText())));
        Profile profile = new Profile(doctor.getId(), doctor.getSlug(), doctor.getPracticeStartYear(),
                doctor.getPublicationsCount(), imageUrl(doctor, images(List.of(doctor))), texts,
                links.serviceIds(doctor.getId()), doctor.getVersion());
        return new Versioned<>(profile, doctor.getVersion());
    }

    private static boolean complete(Map<Language, DoctorTranslation> texts) {
        return texts != null && missing(texts.keySet()).isEmpty();
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

    private static UUID parseDoctorId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw CmsException.doctorNotFound();
        }
    }

    private static UUID parseServiceId(String id) {
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
}
