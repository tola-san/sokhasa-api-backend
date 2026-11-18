package com.sokhasaclinic.clinicbackend.cms.controller;

import com.sokhasaclinic.clinicbackend.cms.dto.PublicPageResponse;
import com.sokhasaclinic.clinicbackend.cms.service.PageService;
import com.sokhasaclinic.clinicbackend.common.dto.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/pages")
public class PublicPageController {

    private final PageService pageService;

    public PublicPageController(PageService pageService) {
        this.pageService = pageService;
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<PublicPageResponse>> getPublicPage(
            @PathVariable String slug,
            @RequestParam String lang) {

        PublicPageResponse response = pageService.getPublicPage(slug, lang);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.of(response));
    }
}
