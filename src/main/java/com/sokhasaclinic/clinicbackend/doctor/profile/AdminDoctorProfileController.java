package com.sokhasaclinic.clinicbackend.doctor.profile;

import com.sokhasaclinic.clinicbackend.cms.dto.ApiResponse;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorProfileDtos.ImageAltText;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorProfileDtos.Profile;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorProfileDtos.ProfileWrite;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorProfileDtos.ServiceDoctors;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorProfileDtos.ServiceDoctorsWrite;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorProfileDtos.Versioned;
import com.sokhasaclinic.clinicbackend.doctor.service.DoctorProfileService;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * ADMIN editing of doctor profiles, photos and service links (PC-05 to PC-07, DP-01 to DP-10).
 * {@code /api/v1/admin/**} below {@code /admin/doctors} is ADMIN only (SecurityConfig). Every write
 * needs {@code If-Match}. Ids are Strings so a malformed id is a 404, not a 400.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminDoctorProfileController {

    private final DoctorProfileService profiles;

    public AdminDoctorProfileController(DoctorProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/doctors/{id}/profile")
    public ResponseEntity<ApiResponse<Profile>> profile(@PathVariable String id) {
        return versioned(profiles.profile(id));
    }

    @PutMapping(path = "/doctors/{id}/profile", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Profile>> updateProfile(@PathVariable String id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody ProfileWrite body) {
        return versioned(profiles.updateProfile(id, ifMatch, body));
    }

    /** {@code altTexts} is a JSON part with exactly one {@code {languageCode, imageAltText}} per language. */
    @PutMapping(path = "/doctors/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Profile>> replaceImage(@PathVariable String id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestPart("image") MultipartFile image,
            @RequestPart("altTexts") List<ImageAltText> altTexts) {
        return versioned(profiles.replaceImage(id, ifMatch, image, altTexts));
    }

    @DeleteMapping("/doctors/{id}/image")
    public ResponseEntity<ApiResponse<Profile>> removeImage(@PathVariable String id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return versioned(profiles.removeImage(id, ifMatch));
    }

    /** The ETag is the service's version (PC-06). */
    @PutMapping(path = "/services/{id}/doctors", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<ServiceDoctors>> replaceServiceDoctors(@PathVariable String id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody ServiceDoctorsWrite body) {
        return versioned(profiles.replaceServiceDoctors(id, ifMatch, body));
    }

    private static <T> ResponseEntity<ApiResponse<T>> versioned(Versioned<T> result) {
        return ResponseEntity.ok()
                .eTag("\"" + result.version() + "\"")
                .cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(result.data()));
    }
}
