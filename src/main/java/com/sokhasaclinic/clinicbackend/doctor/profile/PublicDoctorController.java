package com.sokhasaclinic.clinicbackend.doctor.profile;

import com.sokhasaclinic.clinicbackend.cms.dto.ApiResponse;
import com.sokhasaclinic.clinicbackend.cms.exception.CmsException;
import com.sokhasaclinic.clinicbackend.common.i18n.Language;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorProfileDtos.PublicDoctor;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorProfileDtos.PublicDoctorDetail;
import com.sokhasaclinic.clinicbackend.doctor.service.DoctorProfileService;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public doctor profiles in one language (PC-04, DP-09). Photo URLs are signed for five minutes,
 * so responses are never cached. A doctor's id is never returned.
 */
@RestController
@RequestMapping("/api/v1/public/doctors")
public class PublicDoctorController {

    private final DoctorProfileService profiles;

    public PublicDoctorController(DoctorProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PublicDoctor>>> list(@RequestParam("lang") String lang) {
        Language language = language(lang);
        return localized(language, profiles.publicDoctors(language));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<PublicDoctorDetail>> detail(@PathVariable String slug,
            @RequestParam("lang") String lang) {
        Language language = language(lang);
        return localized(language, profiles.publicDoctor(slug, language));
    }

    private static Language language(String lang) {
        return Language.fromCode(lang).orElseThrow(() -> CmsException.invalid("lang must be one of km, en, zh."));
    }

    private static <T> ResponseEntity<ApiResponse<T>> localized(Language language, T data) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_LANGUAGE, language.code())
                .body(new ApiResponse<>(data));
    }
}
