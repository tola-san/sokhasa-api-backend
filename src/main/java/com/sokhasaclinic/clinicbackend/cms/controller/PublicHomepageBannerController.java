package com.sokhasaclinic.clinicbackend.cms.controller;

import com.sokhasaclinic.clinicbackend.cms.dto.ApiResponse;
import com.sokhasaclinic.clinicbackend.cms.dto.BannerDtos.PublicBanner;
import com.sokhasaclinic.clinicbackend.cms.exception.CmsException;
import com.sokhasaclinic.clinicbackend.cms.service.HomepageBannerService;
import com.sokhasaclinic.clinicbackend.common.i18n.Language;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public read of the published homepage hero banner in one language. The image URL is signed for
 * five minutes, so responses are never cached.
 */
@RestController
@RequestMapping("/api/v1/public/homepage/banner")
public class PublicHomepageBannerController {

    private final HomepageBannerService bannerService;

    public PublicHomepageBannerController(HomepageBannerService bannerService) {
        this.bannerService = bannerService;
    }

    /** {@code lang} is required and must be exactly {@code km}, {@code en} or {@code zh}. */
    @GetMapping
    public ResponseEntity<ApiResponse<PublicBanner>> getBanner(@RequestParam("lang") String lang) {
        Language language = Language.fromCode(lang)
                .orElseThrow(() -> CmsException.invalid("lang must be one of km, en, zh."));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_LANGUAGE, language.code())
                .body(new ApiResponse<>(bannerService.getPublicBanner(language)));
    }
}
