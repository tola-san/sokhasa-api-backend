package com.sokhasaclinic.clinicbackend.cms.controller;

import com.sokhasaclinic.clinicbackend.cms.dto.AboutDtos.*;
import com.sokhasaclinic.clinicbackend.cms.dto.ApiResponse;
import com.sokhasaclinic.clinicbackend.cms.exception.CmsException;
import com.sokhasaclinic.clinicbackend.cms.service.AboutService;
import com.sokhasaclinic.clinicbackend.common.i18n.Language;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class AboutController {
    private final AboutService service;
    public AboutController(AboutService service) { this.service=service; }
    @GetMapping("/public/about")
    public ResponseEntity<ApiResponse<PublicPage>> publicPage(@RequestParam(required=false) String lang) {
        Language language=Language.fromCode(lang).orElseThrow(() -> new CmsException(400,"VALIDATION_ERROR",
                "lang must be one of km, en, zh.",List.of(Map.of("field","lang"))));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Content-Language",lang)
                .body(new ApiResponse<>(service.publicPage(language)));
    }
    @GetMapping("/admin/about")
    public ResponseEntity<ApiResponse<Admin>> admin() { return response(service.admin()); }
    @PutMapping("/admin/about")
    public ResponseEntity<ApiResponse<Admin>> save(@RequestHeader(value="If-Match",required=false) String etag,
            @RequestBody Update body) { return response(service.save(etag,body)); }
    @PutMapping(value="/admin/about/facilities/{id}/image",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Admin>> upload(@PathVariable UUID id,
            @RequestHeader(value="If-Match",required=false) String etag,
            @RequestPart MultipartFile image,@RequestPart List<AltText> altTexts) {
        return response(service.replaceImage(id,etag,image,altTexts));
    }
    @DeleteMapping("/admin/about/facilities/{id}/image")
    public ResponseEntity<ApiResponse<Admin>> remove(@PathVariable UUID id,
            @RequestHeader(value="If-Match",required=false) String etag) { return response(service.removeImage(id,etag)); }
    private static ResponseEntity<ApiResponse<Admin>> response(Versioned v) {
        return ResponseEntity.ok().eTag("\""+v.version()+"\"").cacheControl(CacheControl.noStore()).body(new ApiResponse<>(v.data()));
    }
}
