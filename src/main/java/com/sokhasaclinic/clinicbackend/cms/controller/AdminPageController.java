package com.sokhasaclinic.clinicbackend.cms.controller;

import com.sokhasaclinic.clinicbackend.cms.dto.ApiResponse;
import com.sokhasaclinic.clinicbackend.cms.dto.PageDetailResponse;
import com.sokhasaclinic.clinicbackend.cms.dto.PageListingResponse;
import com.sokhasaclinic.clinicbackend.cms.dto.PublishPageRequest;
import com.sokhasaclinic.clinicbackend.cms.dto.SavePageRequest;
import com.sokhasaclinic.clinicbackend.cms.dto.VersionedPageResponse;
import com.sokhasaclinic.clinicbackend.cms.service.PageService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative controller for CMS page management.
 * Protected by Spring Security and restricted to users with the ADMIN role.
 */
@RestController
@RequestMapping("/api/v1/admin/pages")
public class AdminPageController {

    private final PageService pageService;

    public AdminPageController(PageService pageService) {
        this.pageService = pageService;
    }

    @GetMapping
    public ResponseEntity<PageListingResponse> listPages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "updatedAt,desc") String sort) {
        PageListingResponse response = pageService.listPages(page, size, status, search, sort);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PageDetailResponse>> createPage(
            @Valid @RequestBody SavePageRequest request) {
        VersionedPageResponse result = pageService.createPage(request);
        URI location = URI.create("/api/v1/admin/pages/" + result.data().id());
        return buildVersionedResponse(ResponseEntity.created(location), result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PageDetailResponse>> getPage(@PathVariable UUID id) {
        VersionedPageResponse result = pageService.getPageById(id);
        return buildVersionedResponse(ResponseEntity.ok(), result);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PageDetailResponse>> updatePage(
            @PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false) String matchHeader,
            @Valid @RequestBody SavePageRequest request) {
        VersionedPageResponse result = pageService.updatePage(id, matchHeader, request);
        return buildVersionedResponse(ResponseEntity.ok(), result);
    }

    @PatchMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<PageDetailResponse>> publishPage(
            @PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false) String matchHeader,
            @Valid @RequestBody PublishPageRequest request) {
        VersionedPageResponse result = pageService.publishPage(id, matchHeader, request);
        return buildVersionedResponse(ResponseEntity.ok(), result);
    }

    @PatchMapping("/{id}/unpublish")
    public ResponseEntity<ApiResponse<PageDetailResponse>> unpublishPage(
            @PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false) String matchHeader) {
        VersionedPageResponse result = pageService.unpublishPage(id, matchHeader);
        return buildVersionedResponse(ResponseEntity.ok(), result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePage(
            @PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false) String matchHeader) {
        pageService.deletePage(id, matchHeader);
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }

    private ResponseEntity<ApiResponse<PageDetailResponse>> buildVersionedResponse(
            ResponseEntity.BodyBuilder builder, VersionedPageResponse versioned) {
        return builder.eTag("\"" + versioned.revision() + "\"")
                .cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(versioned.data()));
    }
}
