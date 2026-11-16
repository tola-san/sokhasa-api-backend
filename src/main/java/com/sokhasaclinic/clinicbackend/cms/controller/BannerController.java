package com.sokhasaclinic.clinicbackend.cms.controller;

import com.sokhasaclinic.clinicbackend.cms.dto.ApiResponse;
import com.sokhasaclinic.clinicbackend.cms.dto.BannerDtos.AdminBanner;
import com.sokhasaclinic.clinicbackend.cms.dto.BannerDtos.BannerUpdate;
import com.sokhasaclinic.clinicbackend.cms.dto.BannerDtos.VersionedBanner;
import com.sokhasaclinic.clinicbackend.cms.exception.CmsException;
import com.sokhasaclinic.clinicbackend.cms.service.HomepageBannerService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

    private static final int MAX_TEXT_PART_BYTES = 4 * 1024;

    private final HomepageBannerService bannerService;

    public BannerController(HomepageBannerService bannerService) {
        this.bannerService = bannerService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AdminBanner>> getBanner() {
        return versioned(bannerService.getAdminBanner());
    }

    @PatchMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AdminBanner>> publishBanner(
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestPart(value = "image", required = false) MultipartFile image,
            HttpServletRequest request) throws IOException, ServletException {
        BannerUpdate update = new BannerUpdate(
                image,
                textPart(request, "altText"),
                textPart(request, "heading"),
                textPart(request, "description"));
        return versioned(bannerService.publish(ifMatch, update));
    }

    // Text parts are read as UTF-8 directly: servlet containers only expose form parameters for POST.
    private static String textPart(HttpServletRequest request, String name) throws IOException, ServletException {
        Part part = request.getPart(name);
        if (part == null) {
            return null;
        }
        if (part.getSubmittedFileName() != null) {
            throw CmsException.invalid(name + " must be a text field.");
        }
        try (InputStream input = part.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_TEXT_PART_BYTES + 1);
            if (bytes.length > MAX_TEXT_PART_BYTES) {
                throw CmsException.invalid(name + " is too long.");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static ResponseEntity<ApiResponse<AdminBanner>> versioned(VersionedBanner banner) {
        return ResponseEntity.ok()
                .eTag("\"" + banner.revision() + "\"")
                .cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(banner.data()));
    }
}
