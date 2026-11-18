package com.sokhasaclinic.clinicbackend.cms.controller;

import com.sokhasaclinic.clinicbackend.cms.dto.PageDetailResponse;
import com.sokhasaclinic.clinicbackend.cms.dto.PageRequest;
import com.sokhasaclinic.clinicbackend.cms.dto.PageSummaryResponse;
import com.sokhasaclinic.clinicbackend.cms.dto.PublishRequest;
import com.sokhasaclinic.clinicbackend.cms.model.Page;
import com.sokhasaclinic.clinicbackend.cms.service.PageService;
import com.sokhasaclinic.clinicbackend.common.dto.ApiResponse;
import com.sokhasaclinic.clinicbackend.common.dto.ApiResponsePage;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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

@RestController
@RequestMapping("/api/v1/admin/pages")
public class AdminPageController {

    private final PageService pageService;

    public AdminPageController(PageService pageService) {
        this.pageService = pageService;
    }

    @GetMapping
    public ResponseEntity<ApiResponsePage<PageSummaryResponse>> listPages(
            @RequestParam(required = false) Page.Status status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {

        ApiResponsePage<PageSummaryResponse> result = pageService.listPages(status, search, pageable);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PageDetailResponse>> createPage(@Valid @RequestBody PageRequest request) {
        Page page = pageService.createPage(request);
        PageDetailResponse detail = PageDetailResponse.fromEntity(page);
        String etag = "\"" + page.getVersion() + "\"";
        URI location = URI.create("/api/v1/admin/pages/" + page.getId());

        return ResponseEntity.created(location)
                .eTag(etag)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.of(detail));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PageDetailResponse>> getPage(@PathVariable UUID id) {
        Page page = pageService.getPageEntity(id);
        PageDetailResponse detail = PageDetailResponse.fromEntity(page);
        String etag = "\"" + page.getVersion() + "\"";

        return ResponseEntity.ok()
                .eTag(etag)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.of(detail));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PageDetailResponse>> updatePage(
            @PathVariable UUID id,
            @Valid @RequestBody PageRequest request,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {

        Page page = pageService.updatePage(id, request, ifMatch);
        PageDetailResponse detail = PageDetailResponse.fromEntity(page);
        String etag = "\"" + page.getVersion() + "\"";

        return ResponseEntity.ok()
                .eTag(etag)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.of(detail));
    }

    @PatchMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<PageDetailResponse>> publishPage(
            @PathVariable UUID id,
            @Valid @RequestBody PublishRequest request,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {

        Page page = pageService.publishPage(id, request, ifMatch);
        PageDetailResponse detail = PageDetailResponse.fromEntity(page);
        String etag = "\"" + page.getVersion() + "\"";

        return ResponseEntity.ok()
                .eTag(etag)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.of(detail));
    }

    @PatchMapping("/{id}/unpublish")
    public ResponseEntity<ApiResponse<PageDetailResponse>> unpublishPage(
            @PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {

        Page page = pageService.unpublishPage(id, ifMatch);
        PageDetailResponse detail = PageDetailResponse.fromEntity(page);
        String etag = "\"" + page.getVersion() + "\"";

        return ResponseEntity.ok()
                .eTag(etag)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.of(detail));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePage(
            @PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {

        pageService.deletePage(id, ifMatch);
        return ResponseEntity.noContent().build();
    }
}
