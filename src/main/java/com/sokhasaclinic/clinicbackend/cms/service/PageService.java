package com.sokhasaclinic.clinicbackend.cms.service;

import com.sokhasaclinic.clinicbackend.cms.dto.PageDetailResponse;
import com.sokhasaclinic.clinicbackend.cms.dto.PageRequest;
import com.sokhasaclinic.clinicbackend.cms.dto.PageSummaryResponse;
import com.sokhasaclinic.clinicbackend.cms.dto.PublicPageResponse;
import com.sokhasaclinic.clinicbackend.cms.dto.PublishRequest;
import com.sokhasaclinic.clinicbackend.cms.dto.TranslationDto;
import com.sokhasaclinic.clinicbackend.cms.model.Page;
import com.sokhasaclinic.clinicbackend.cms.model.PageTranslation;
import com.sokhasaclinic.clinicbackend.cms.repository.PageRepository;
import com.sokhasaclinic.clinicbackend.common.dto.ApiResponsePage;
import com.sokhasaclinic.clinicbackend.common.exception.ConflictException;
import com.sokhasaclinic.clinicbackend.common.exception.PreconditionFailedException;
import com.sokhasaclinic.clinicbackend.common.exception.PreconditionRequiredException;
import com.sokhasaclinic.clinicbackend.common.exception.ResourceNotFoundException;
import com.sokhasaclinic.clinicbackend.common.exception.ValidationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PageService {

    private final PageRepository pageRepository;

    public PageService(PageRepository pageRepository) {
        this.pageRepository = pageRepository;
    }

    @Transactional(readOnly = true)
    public ApiResponsePage<PageSummaryResponse> listPages(Page.Status status, String search, Pageable pageable) {
        org.springframework.data.domain.Page<Page> pages = pageRepository.findWithFilter(status, search, pageable);
        return ApiResponsePage.of(pages.map(PageSummaryResponse::fromEntity));
    }

    @Transactional(readOnly = true)
    public Page getPageEntity(UUID id) {
        return pageRepository.findByIdWithTranslations(id)
                .orElseThrow(() -> new ResourceNotFoundException("Page not found: " + id, "PAGE_NOT_FOUND"));
    }

    @Transactional(readOnly = true)
    public PageDetailResponse getPageDetail(UUID id) {
        Page page = getPageEntity(id);
        return PageDetailResponse.fromEntity(page);
    }

    @Transactional
    public Page createPage(PageRequest request) {
        if (pageRepository.existsBySlug(request.slug())) {
            throw new ConflictException("Slug already exists: " + request.slug(), "SLUG_ALREADY_EXISTS");
        }

        validateTranslations(request.translations());

        Page page = Page.builder()
                .slug(request.slug().trim().toLowerCase())
                .status(Page.Status.DRAFT)
                .version(1L)
                .publishedLanguageCodes(new String[0])
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .translations(new ArrayList<>())
                .build();

        for (TranslationDto trDto : request.translations()) {
            PageTranslation translation = PageTranslation.builder()
                    .page(page)
                    .languageCode(trDto.languageCode().trim().toLowerCase())
                    .title(trDto.title().trim())
                    .content(trDto.content().trim())
                    .mediaId(trDto.image() != null ? trDto.image().mediaId() : null)
                    .altText(trDto.image() != null ? trDto.image().altText() : null)
                    .build();
            page.getTranslations().add(translation);
        }

        return pageRepository.save(page);
    }

    @Transactional
    public Page updatePage(UUID id, PageRequest request, String ifMatch) {
        Page page = getPageEntity(id);
        verifyIfMatch(page, ifMatch);

        if (!page.getSlug().equals(request.slug().trim().toLowerCase())) {
            throw new ValidationException("Slug cannot be changed after creation.", "SLUG_IMMUTABLE");
        }

        validateTranslations(request.translations());

        page.setUpdatedAt(Instant.now());
        page.getTranslations().clear();

        for (TranslationDto trDto : request.translations()) {
            PageTranslation translation = PageTranslation.builder()
                    .page(page)
                    .languageCode(trDto.languageCode().trim().toLowerCase())
                    .title(trDto.title().trim())
                    .content(trDto.content().trim())
                    .mediaId(trDto.image() != null ? trDto.image().mediaId() : null)
                    .altText(trDto.image() != null ? trDto.image().altText() : null)
                    .build();
            page.getTranslations().add(translation);
        }

        return pageRepository.save(page);
    }

    @Transactional
    public Page publishPage(UUID id, PublishRequest request, String ifMatch) {
        Page page = getPageEntity(id);
        verifyIfMatch(page, ifMatch);

        Set<String> workingCodes = new HashSet<>();
        for (PageTranslation t : page.getTranslations()) {
            workingCodes.add(t.getLanguageCode());
        }

        for (String reqLang : request.languageCodes()) {
            if (!workingCodes.contains(reqLang)) {
                throw new ConflictException("Language not found in working copy: " + reqLang, "TRANSLATION_MISSING");
            }
        }

        Set<String> targetLangs = new HashSet<>(request.languageCodes());
        for (PageTranslation t : page.getTranslations()) {
            if (targetLangs.contains(t.getLanguageCode())) {
                t.setPublishedTitle(t.getTitle());
                t.setPublishedContent(t.getContent());
                t.setPublishedMediaId(t.getMediaId());
                t.setPublishedAltText(t.getAltText());
            } else {
                t.setPublishedTitle(null);
                t.setPublishedContent(null);
                t.setPublishedMediaId(null);
                t.setPublishedAltText(null);
            }
        }

        page.setStatus(Page.Status.PUBLISHED);
        page.setPublishedLanguageCodes(request.languageCodes().toArray(new String[0]));
        page.setPublishedAt(Instant.now());
        page.setUpdatedAt(Instant.now());

        return pageRepository.save(page);
    }

    @Transactional
    public Page unpublishPage(UUID id, String ifMatch) {
        Page page = getPageEntity(id);
        verifyIfMatch(page, ifMatch);

        if (page.getStatus() == Page.Status.DRAFT) {
            // Already draft: succeeds without changing version or timestamps
            return page;
        }

        for (PageTranslation t : page.getTranslations()) {
            t.setPublishedTitle(null);
            t.setPublishedContent(null);
            t.setPublishedMediaId(null);
            t.setPublishedAltText(null);
        }

        page.setStatus(Page.Status.DRAFT);
        page.setPublishedLanguageCodes(new String[0]);
        page.setPublishedAt(null);
        page.setUpdatedAt(Instant.now());

        return pageRepository.save(page);
    }

    @Transactional
    public void deletePage(UUID id, String ifMatch) {
        Page page = getPageEntity(id);
        verifyIfMatch(page, ifMatch);

        if (page.getStatus() == Page.Status.PUBLISHED) {
            throw new ConflictException("Cannot delete a published page. Unpublish it first.", "PAGE_IS_PUBLISHED");
        }

        pageRepository.delete(page);
    }

    @Transactional(readOnly = true)
    public PublicPageResponse getPublicPage(String slug, String lang) {
        if (lang == null || (!lang.equals("km") && !lang.equals("en") && !lang.equals("zh"))) {
            throw new ValidationException("Language must be km, en, or zh.", "VALIDATION_ERROR");
        }

        Page page = pageRepository.findBySlugWithTranslations(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Page not found: " + slug, "PAGE_NOT_FOUND"));

        if (page.getStatus() != Page.Status.PUBLISHED) {
            throw new ResourceNotFoundException("Page is not published: " + slug, "PAGE_NOT_FOUND");
        }

        List<String> publishedCodes = page.getPublishedLanguageCodes() != null
                ? Arrays.asList(page.getPublishedLanguageCodes())
                : List.of();

        if (!publishedCodes.contains(lang)) {
            throw new ResourceNotFoundException("Language translation not published: " + lang, "PAGE_NOT_FOUND");
        }

        Optional<PageTranslation> trOpt = page.getTranslations().stream()
                .filter(t -> t.getLanguageCode().equals(lang) && t.getPublishedTitle() != null)
                .findFirst();

        if (trOpt.isEmpty()) {
            throw new ResourceNotFoundException("Translation not found: " + lang, "PAGE_NOT_FOUND");
        }

        PageTranslation tr = trOpt.get();
        PublicPageResponse.PublicImage img = null;
        if (tr.getPublishedMediaId() != null) {
            img = new PublicPageResponse.PublicImage(
                    "/api/v1/public/media/" + tr.getPublishedMediaId(),
                    tr.getPublishedAltText()
            );
        }

        return new PublicPageResponse(
                page.getSlug(),
                page.getStatus(),
                lang,
                tr.getPublishedTitle(),
                tr.getPublishedContent(),
                img
        );
    }

    private void verifyIfMatch(Page page, String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException("If-Match header is required.", "PAGE_VERSION_REQUIRED");
        }

        String currentEtag = "\"" + page.getVersion() + "\"";
        String cleanIfMatch = ifMatch.trim();

        if (!cleanIfMatch.equals(currentEtag) && !cleanIfMatch.equals(String.valueOf(page.getVersion()))) {
            throw new PreconditionFailedException("Stale page version. Reload the current page before reapplying changes.", "PAGE_VERSION_MISMATCH");
        }
    }

    private void validateTranslations(List<TranslationDto> translations) {
        Set<String> seen = new HashSet<>();
        for (TranslationDto t : translations) {
            if (!seen.add(t.languageCode().trim().toLowerCase())) {
                throw new ValidationException("Duplicate language code in translations: " + t.languageCode(), "VALIDATION_ERROR");
            }
            if (t.image() != null && (t.image().altText() == null || t.image().altText().isBlank())) {
                throw new ValidationException("Alt text is required when an image is set.", "VALIDATION_ERROR");
            }
        }
    }
}
