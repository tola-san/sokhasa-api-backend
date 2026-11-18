package com.sokhasaclinic.clinicbackend.clinicservice.controller;

import com.sokhasaclinic.clinicbackend.clinicservice.dto.PublicServiceResponse;
import com.sokhasaclinic.clinicbackend.clinicservice.service.ServiceCatalogService;
import com.sokhasaclinic.clinicbackend.cms.dto.ApiResponse;
import com.sokhasaclinic.clinicbackend.cms.exception.CmsException;
import com.sokhasaclinic.clinicbackend.common.i18n.Language;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public list of bookable services in one language, for the booking form. Image URLs are signed
 * for five minutes, so responses are never cached.
 */
@RestController
@RequestMapping("/api/v1/public/services")
public class PublicServiceController {

    private final ServiceCatalogService catalogService;

    public PublicServiceController(ServiceCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /** {@code lang} is required and must be exactly {@code km}, {@code en} or {@code zh}. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<PublicServiceResponse>>> listServices(@RequestParam("lang") String lang) {
        Language language = Language.fromCode(lang)
                .orElseThrow(() -> CmsException.invalid("lang must be one of km, en, zh."));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_LANGUAGE, language.code())
                .body(new ApiResponse<>(catalogService.listPublicServices(language)));
    }
}
