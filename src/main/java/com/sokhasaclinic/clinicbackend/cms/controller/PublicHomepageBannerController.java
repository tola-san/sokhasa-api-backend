package com.sokhasaclinic.clinicbackend.cms.controller;

import com.sokhasaclinic.clinicbackend.cms.dto.ApiResponse;
import com.sokhasaclinic.clinicbackend.cms.dto.BannerDtos.PublicBanner;
import com.sokhasaclinic.clinicbackend.cms.service.HomepageBannerService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public read of the published homepage hero banner. The image URL is signed for five minutes,
 * so responses are never cached.
 */
@RestController
@RequestMapping("/api/v1/public/homepage/banner")
public class PublicHomepageBannerController {

    private final HomepageBannerService bannerService;

    public PublicHomepageBannerController(HomepageBannerService bannerService) {
        this.bannerService = bannerService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PublicBanner>> getBanner() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(bannerService.getPublicBanner()));
    }
}
