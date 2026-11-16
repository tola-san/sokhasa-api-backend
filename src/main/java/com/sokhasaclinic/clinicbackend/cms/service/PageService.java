package com.sokhasaclinic.clinicbackend.cms.service;

import com.sokhasaclinic.clinicbackend.cms.dto.CmsDtos;
import com.sokhasaclinic.clinicbackend.cms.dto.ImageDto;
import com.sokhasaclinic.clinicbackend.cms.dto.PageDetailResponse;
import com.sokhasaclinic.clinicbackend.cms.dto.PageListingResponse;
import com.sokhasaclinic.clinicbackend.cms.dto.PageSummaryResponse;
import com.sokhasaclinic.clinicbackend.cms.dto.PageTitleDto;
import com.sokhasaclinic.clinicbackend.cms.dto.PaginationDto;
import com.sokhasaclinic.clinicbackend.cms.dto.PublishPageRequest;
import com.sokhasaclinic.clinicbackend.cms.dto.PublicPageResponse;
import com.sokhasaclinic.clinicbackend.cms.dto.SavePageRequest;
import com.sokhasaclinic.clinicbackend.cms.dto.SignedImageDto;
import com.sokhasaclinic.clinicbackend.cms.dto.TranslationDto;
import com.sokhasaclinic.clinicbackend.cms.dto.VersionedPageResponse;
import com.sokhasaclinic.clinicbackend.cms.exception.CmsException;
import com.sokhasaclinic.clinicbackend.cms.exception.PageConflictException;
import com.sokhasaclinic.clinicbackend.cms.exception.PageNotFoundException;
import com.sokhasaclinic.clinicbackend.cms.exception.PageVersionMismatchException;
import com.sokhasaclinic.clinicbackend.cms.exception.PageVersionRequiredException;
import com.sokhasaclinic.clinicbackend.cms.model.CmsMedia;
import com.sokhasaclinic.clinicbackend.cms.model.CmsPage;
import com.sokhasaclinic.clinicbackend.cms.model.CmsTranslation;
import com.sokhasaclinic.clinicbackend.cms.repository.MediaRepository;
import com.sokhasaclinic.clinicbackend.cms.repository.PageRepository;
import com.sokhasaclinic.clinicbackend.cms.service.storage.MediaStorage;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core business service for CMS page authoring, lifecycle management, and public retrieval.
 */
@Service
@Transactional(readOnly = true)
public class PageService {

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("km", "en", "zh");

    private final PageRepository pageRepository;
    private final MediaRepository mediaRepository;
    private final MediaStorage mediaStorage;

    public PageService(PageRepository pageRepository, MediaRepository mediaRepository, MediaStorage mediaStorage) {
        this.pageRepository = pageRepository;
        this.mediaRepository = mediaRepository;
        this.mediaStorage = mediaStorage;
    }

    /**
     * Retrieves page detail by its unique ID.
     */
    public VersionedPageResponse getPageById(UUID id) {
        CmsPage page = pageRepository.findById(id).orElseThrow(PageNotFoundException::new);
        return toVersionedResponse(page);
    }

    /**
     * Creates a new CMS page with the provided draft translations.
     */
    @Transactional
    public VersionedPageResponse createPage(SavePageRequest request) {
        CmsPage page = new CmsPage(request.slug());
        List<CmsTranslation> translations = buildValidatedTranslations(request.translations());
        for (CmsTranslation translation : translations) {
            page.addTranslation(translation);
        }
        return toVersionedResponse(pageRepository.saveAndFlush(page));
    }

    /**
     * Updates an existing page's draft translations with optimistic revision matching.
     */
    @Transactional
    public VersionedPageResponse updatePage(UUID id, String ifMatchHeader, SavePageRequest request) {
        CmsPage page = findAndLockPage(id, ifMatchHeader);
        if (!page.getSlug().equals(request.slug())) {
            throw CmsException.invalid("The slug is immutable.");
        }

        List<CmsTranslation> replacements = buildValidatedTranslations(request.translations());
        page.updateDrafts(replacements);
        return toVersionedResponse(page);
    }

    /**
     * Publishes selected language translations from the draft set.
     */
    @Transactional
    public VersionedPageResponse publishPage(UUID id, String ifMatchHeader, PublishPageRequest request) {
        CmsPage page = findAndLockPage(id, ifMatchHeader);
        Set<String> languages = new LinkedHashSet<>(request.languageCodes());
        if (languages.size() != request.languageCodes().size()) {
            throw CmsException.invalid("Duplicate language codes.");
        }

        List<CmsTranslation> selected = page.getDraftTranslations().stream()
                .filter(t -> languages.contains(t.getLanguageCode()))
                .map(t -> t.withPublished(true))
                .toList();

        if (selected.size() != languages.size()) {
            throw new PageConflictException("TRANSLATION_MISSING", "A selected translation is missing.");
        }

        page.publishTranslations(selected);
        return toVersionedResponse(page);
    }

    /**
     * Unpublishes a page, discarding live translations while preserving draft content.
     */
    @Transactional
    public VersionedPageResponse unpublishPage(UUID id, String ifMatchHeader) {
        CmsPage page = findAndLockPage(id, ifMatchHeader);
        page.unpublish();
        return toVersionedResponse(page);
    }

    /**
     * Deletes an unpublished page.
     */
    @Transactional
    public void deletePage(UUID id, String ifMatchHeader) {
        CmsPage page = findAndLockPage(id, ifMatchHeader);
        if (page.isPublished()) {
            throw new PageConflictException("PAGE_IS_PUBLISHED", "Unpublish the page before deletion.");
        }
        pageRepository.delete(page);
    }

    /**
     * Retrieves the published content for a given page slug and language code.
     */
    public PublicPageResponse getPublishedPage(String slug, List<String> languages) {
        if (languages == null || languages.size() != 1 || !SUPPORTED_LANGUAGES.contains(languages.getFirst())) {
            throw CmsException.invalid("Supply exactly one supported language.");
        }

        String targetLang = languages.getFirst();
        CmsPage page = pageRepository.findBySlug(slug).orElseThrow(PageNotFoundException::new);

        CmsTranslation liveTranslation = page.getLiveTranslations().stream()
                .filter(t -> t.getLanguageCode().equals(targetLang))
                .findFirst()
                .orElseThrow(PageNotFoundException::new);

        SignedImageDto imageDto = null;
        if (liveTranslation.getMediaId() != null) {
            CmsMedia media = mediaRepository.findById(liveTranslation.getMediaId())
                    .orElseThrow(PageNotFoundException::new);
            imageDto = new SignedImageDto(mediaStorage.signedUrl(media.getObjectKey()), liveTranslation.getAltText());
        }

        return new PublicPageResponse(
                slug,
                "PUBLISHED",
                liveTranslation.getLanguageCode(),
                liveTranslation.getTitle(),
                liveTranslation.getContent(),
                imageDto
        );
    }

    /**
     * Lists paginated page summaries filtered by status and search terms.
     */
    public PageListingResponse listPages(int page, int size, String status, String search, String sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw CmsException.invalid("Invalid pagination.");
        }
        if (status != null && !Set.of("DRAFT", "PUBLISHED").contains(status)) {
            throw CmsException.invalid("Invalid status.");
        }
        if (sort == null || !sort.matches("(updatedAt|createdAt|slug),(asc|desc)")) {
            throw CmsException.invalid("Invalid sort.");
        }

        String[] sortParts = sort.split(",");
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.fromString(sortParts[1]), sortParts[0]).and(Sort.by("id"))
        );

        Page<CmsPage> pagedResult = pageRepository.findAll((root, query, builder) -> {
            List<Predicate> filters = new ArrayList<>();
            if (status != null) {
                filters.add("DRAFT".equals(status)
                        ? builder.isNull(root.get("publishedAt"))
                        : builder.isNotNull(root.get("publishedAt")));
            }
            if (search != null && !search.isBlank()) {
                String term = "%" + search.trim().toLowerCase(Locale.ROOT)
                        .replace("!", "!!")
                        .replace("%", "!%")
                        .replace("_", "!_") + "%";
                var translationJoin = root.join("translations");
                filters.add(builder.or(
                        builder.like(builder.lower(root.get("slug")), term, '!'),
                        builder.and(
                                builder.isFalse(translationJoin.get("published")),
                                builder.like(builder.lower(translationJoin.get("title")), term, '!')
                        )
                ));
                query.distinct(true);
            }
            return builder.and(filters.toArray(Predicate[]::new));
        }, pageable);

        List<PageSummaryResponse> summaries = pagedResult.getContent().stream()
                .map(this::toSummaryResponse)
                .toList();

        return new PageListingResponse(
                summaries,
                new PaginationDto(
                        page,
                        size,
                        pagedResult.getTotalElements(),
                        pagedResult.getTotalPages(),
                        pagedResult.hasNext(),
                        pagedResult.hasPrevious()
                )
        );
    }

    // --- Backward compatibility delegate methods ---

    public VersionedPageResponse get(UUID id) {
        return getPageById(id);
    }

    public VersionedPageResponse create(SavePageRequest request) {
        return createPage(request);
    }

    public VersionedPageResponse create(CmsDtos.Save request) {
        return createPage(request.toRequest());
    }

    public VersionedPageResponse save(UUID id, String match, SavePageRequest request) {
        return updatePage(id, match, request);
    }

    public VersionedPageResponse save(UUID id, String match, CmsDtos.Save request) {
        return updatePage(id, match, request.toRequest());
    }

    public VersionedPageResponse publish(UUID id, String match, PublishPageRequest request) {
        return publishPage(id, match, request);
    }

    public VersionedPageResponse publish(UUID id, String match, CmsDtos.Publish request) {
        return publishPage(id, match, request.toRequest());
    }

    public VersionedPageResponse unpublish(UUID id, String match) {
        return unpublishPage(id, match);
    }

    public void delete(UUID id, String match) {
        deletePage(id, match);
    }

    public PublicPageResponse published(String slug, List<String> languages) {
        return getPublishedPage(slug, languages);
    }

    public PageListingResponse list(int page, int size, String status, String search, String sort) {
        return listPages(page, size, status, search, sort);
    }

    // --- Private helper methods ---

    private CmsPage findAndLockPage(UUID id, String match) {
        if (match == null) {
            throw new PageVersionRequiredException();
        }
        if (!match.matches("\"[1-9][0-9]*\"")) {
            throw CmsException.invalid("Invalid If-Match.");
        }

        CmsPage page = pageRepository.lock(id).orElseThrow(PageNotFoundException::new);
        if (!match.equals("\"" + page.getRevision() + "\"")) {
            throw new PageVersionMismatchException();
        }
        return page;
    }

    private List<CmsTranslation> buildValidatedTranslations(List<TranslationDto> dtoList) {
        Set<String> languages = new HashSet<>();
        return dtoList.stream().map(dto -> {
            if (!languages.add(dto.languageCode())) {
                throw CmsException.invalid("Duplicate language codes.");
            }

            String sanitizedContent = Jsoup.clean(dto.content(), Safelist.basic());
            if (Jsoup.parse(sanitizedContent).text().isBlank()) {
                throw CmsException.invalid("Content must contain text.");
            }

            UUID mediaId = null;
            String altText = null;
            if (dto.image() != null) {
                if (!mediaRepository.existsById(dto.image().mediaId())) {
                    throw CmsException.invalid("Unknown media ID.");
                }
                mediaId = dto.image().mediaId();
                altText = dto.image().altText().trim();
            }

            return new CmsTranslation(
                    dto.languageCode(),
                    dto.title().trim(),
                    sanitizedContent,
                    mediaId,
                    altText,
                    false
            );
        }).toList();
    }

    private VersionedPageResponse toVersionedResponse(CmsPage page) {
        List<TranslationDto> draftDtos = page.getDraftTranslations().stream()
                .map(this::toTranslationDto)
                .toList();

        List<String> publishedLangs = page.getLiveTranslations().stream()
                .map(CmsTranslation::getLanguageCode)
                .toList();

        PageDetailResponse detail = new PageDetailResponse(
                page.getId(),
                page.getSlug(),
                page.getStatus(),
                draftDtos,
                publishedLangs,
                page.getPublishedAt(),
                page.getCreatedAt(),
                page.getUpdatedAt()
        );

        return new VersionedPageResponse(detail, page.getRevision());
    }

    private PageSummaryResponse toSummaryResponse(CmsPage page) {
        List<PageTitleDto> titleDtos = page.getDraftTranslations().stream()
                .map(t -> new PageTitleDto(t.getLanguageCode(), t.getTitle()))
                .toList();

        List<String> publishedLangs = page.getLiveTranslations().stream()
                .map(CmsTranslation::getLanguageCode)
                .toList();

        return new PageSummaryResponse(
                page.getId(),
                page.getSlug(),
                page.getStatus(),
                titleDtos,
                publishedLangs,
                page.getPublishedAt(),
                page.getCreatedAt(),
                page.getUpdatedAt()
        );
    }

    private TranslationDto toTranslationDto(CmsTranslation translation) {
        ImageDto imageDto = translation.getMediaId() != null
                ? new ImageDto(translation.getMediaId(), translation.getAltText())
                : null;
        return new TranslationDto(
                translation.getLanguageCode(),
                translation.getTitle(),
                translation.getContent(),
                imageDto
        );
    }
}
