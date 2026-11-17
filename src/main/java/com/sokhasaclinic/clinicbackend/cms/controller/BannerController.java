package com.sokhasaclinic.clinicbackend.cms.controller;

import com.sokhasaclinic.clinicbackend.cms.dto.ApiResponse;
import com.sokhasaclinic.clinicbackend.cms.dto.BannerDtos.AdminBanner;
import com.sokhasaclinic.clinicbackend.cms.dto.BannerDtos.BannerText;
import com.sokhasaclinic.clinicbackend.cms.dto.BannerDtos.BannerUpdate;
import com.sokhasaclinic.clinicbackend.cms.dto.BannerDtos.VersionedBanner;
import com.sokhasaclinic.clinicbackend.cms.service.HomepageBannerService;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Admin endpoints for the homepage hero banner. Requires the ADMIN role.
 */
@RestController
@RequestMapping("/api/v1/admin/homepage/banner")
public class BannerController {

    private final HomepageBannerService bannerService;

    public BannerController(HomepageBannerService bannerService) {
        this.bannerService = bannerService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AdminBanner>> getBanner() {
        return versioned(bannerService.getAdminBanner());
    }

    /**
     * {@code translations} is a JSON part ({@code application/json}) holding an array of
     * {@code {languageCode, heading, description, altText}}. Malformed JSON returns 400.
     */
    @PatchMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AdminBanner>> publishBanner(
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @RequestPart(value = "translations", required = false) List<BannerText> translations) {
        return versioned(bannerService.publish(ifMatch, new BannerUpdate(image, translations)));
    }

    private static ResponseEntity<ApiResponse<AdminBanner>> versioned(VersionedBanner banner) {
        return ResponseEntity.ok()
                .eTag("\"" + banner.revision() + "\"")
                .cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(banner.data()));
    }
}
