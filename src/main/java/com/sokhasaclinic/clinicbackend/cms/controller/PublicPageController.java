package com.sokhasaclinic.clinicbackend.cms.controller;

import com.sokhasaclinic.clinicbackend.cms.dto.ApiResponse;
import com.sokhasaclinic.clinicbackend.cms.dto.PublicPageResponse;
import com.sokhasaclinic.clinicbackend.cms.service.PageService;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public controller serving published CMS page content for clinic visitors.
 */
@RestController
@RequestMapping("/api/v1/public/pages")
public class PublicPageController {

    private final PageService pageService;

    public PublicPageController(PageService pageService) {
        this.pageService = pageService;
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<PublicPageResponse>> getPublishedPage(
            @PathVariable String slug,
            @RequestParam List<String> lang) {
        PublicPageResponse response = pageService.getPublishedPage(slug, lang);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(response));
    }
}
