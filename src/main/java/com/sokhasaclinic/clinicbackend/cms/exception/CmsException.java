package com.sokhasaclinic.clinicbackend.cms.exception;

import com.sokhasaclinic.clinicbackend.common.i18n.Language;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Base domain exception for CMS operations.
 */
public class CmsException extends RuntimeException {

    private final int status;
    private final String type;
    private final List<Map<String, Object>> errors;

    public CmsException(int status, String type, String message) {
        this(status, type, message, List.of());
    }

    public CmsException(int status, String type, String message, List<Map<String, Object>> errors) {
        super(message);
        this.status = status;
        this.type = type;
        this.errors = List.copyOf(errors);
    }

    public CmsException(int status, String type, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.type = type;
        this.errors = List.of();
    }

    /** Problem-details {@code errors} entries; empty when the detail message says it all. */
    public List<Map<String, Object>> getErrors() {
        return errors;
    }

    public int getStatus() {
        return status;
    }

    public String getType() {
        return type;
    }

    public static CmsException invalid(String message) {
        return new CmsException(400, "VALIDATION_ERROR", message);
    }

    public static CmsException versionRequired() {
        return new CmsException(428, "VERSION_REQUIRED", "If-Match is required.");
    }

    public static CmsException versionMismatch() {
        return new CmsException(412, "VERSION_MISMATCH", "Reload the banner before saving.");
    }

    public static CmsException bannerNotFound() {
        return new CmsException(404, "BANNER_NOT_FOUND", "No homepage banner has been published.");
    }

    public static CmsException bannerNotFound(Language language) {
        return new CmsException(404, "BANNER_NOT_FOUND",
                "No homepage banner has been published in " + language.code() + ".");
    }

    /** A required language would be missing after the request. Lists each missing code. */
    public static CmsException translationMissing(Collection<Language> missing) {
        return translationMissing(missing, "Add the banner text in every language before publishing.");
    }

    /** An active service would be missing a language (decision SA-06). */
    public static CmsException serviceTranslationMissing(Collection<Language> missing) {
        return translationMissing(missing, "An active service needs its name and description in every language.");
    }

    private static CmsException translationMissing(Collection<Language> missing, String message) {
        List<String> codes = missing.stream().map(Language::code).toList();
        List<Map<String, Object>> errors = codes.stream()
                .<Map<String, Object>>map(code -> Map.of("field", "translations", "languageCode", code))
                .toList();
        return new CmsException(409, "TRANSLATION_MISSING",
                message + " Missing: " + String.join(", ", codes) + ".",
                errors);
    }

    public static CmsException serviceNotFound() {
        return new CmsException(404, "SERVICE_NOT_FOUND", "Service not found.");
    }

    public static CmsException serviceVersionMismatch() {
        return new CmsException(412, "VERSION_MISMATCH", "Reload the service before saving.");
    }

    public static CmsException doctorNotFound() {
        return new CmsException(404, "DOCTOR_NOT_FOUND", "Doctor not found.");
    }

    public static CmsException doctorVersionMismatch() {
        return new CmsException(412, "VERSION_MISMATCH", "Reload the doctor before saving.");
    }

    public static CmsException doctorSlugTaken() {
        return new CmsException(409, "DOCTOR_SLUG_TAKEN", "Another doctor already uses this slug.",
                List.of(Map.of("field", "slug", "message", "Choose a different slug.")));
    }

    /** DP-07: a photo needs the doctor's profile in every language. */
    public static CmsException doctorTranslationMissing(Collection<Language> missing) {
        return translationMissing(missing, "Save the doctor's profile in every language before adding a photo.");
    }
}
