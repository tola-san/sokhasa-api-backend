package com.sokhasaclinic.clinicbackend.doctor.dto;

import java.util.List;
import java.util.UUID;

/** Doctor profile, photo and service-link bodies (PC-04 to PC-08, DP-01 to DP-11). */
public final class DoctorProfileDtos {

    private DoctorProfileDtos() {
    }

    /** {@code PUT /admin/doctors/{id}/profile}. Exactly one translation per language. */
    public record ProfileWrite(String slug, Integer practiceStartYear, Integer publicationsCount,
            List<ProfileText> translations) { }

    /** {@code imageAltText}: only while the doctor has a photo; null keeps the saved value (DP-08). */
    public record ProfileText(String languageCode, String title, String bio, String education, List<String> tags,
            String imageAltText) { }

    /** Admin profile. {@code slug} is null and {@code translations} empty until a profile is saved. */
    public record Profile(UUID doctorId, String slug, Integer practiceStartYear, Integer publicationsCount,
            String imageUrl, List<ProfileText> translations, List<UUID> serviceIds, long version) { }

    public record ImageAltText(String languageCode, String imageAltText) { }

    /** A public doctor list item: every card field in one language (DP-09). */
    public record PublicDoctor(String slug, String fullName, String title, String bio, String education,
            List<String> tags, Integer experienceYears, Integer publicationsCount, String imageUrl,
            String imageAltText, List<String> serviceSlugs) { }

    public record PublicDoctorService(String slug, String name, String shortDescription) { }

    public record PublicDoctorDetail(String slug, String fullName, String title, String bio, String education,
            List<String> tags, Integer experienceYears, Integer publicationsCount, String imageUrl,
            String imageAltText, List<String> serviceSlugs, List<PublicDoctorService> services) { }

    public record ServiceDoctorsWrite(List<UUID> doctorIds) { }

    public record ServiceDoctors(UUID serviceId, List<UUID> doctorIds) { }

    /** A response body with the version that becomes its {@code ETag}. */
    public record Versioned<T>(T data, long version) { }
}
