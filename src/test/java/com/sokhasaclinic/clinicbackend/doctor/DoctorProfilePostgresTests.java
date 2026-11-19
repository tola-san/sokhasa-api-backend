package com.sokhasaclinic.clinicbackend.doctor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.sokhasaclinic.clinicbackend.cms.exception.CmsException;
import com.sokhasaclinic.clinicbackend.cms.service.storage.MediaStorage;
import com.sokhasaclinic.clinicbackend.common.i18n.Language;
import com.sokhasaclinic.clinicbackend.common.time.ClinicTime;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorProfileDtos.ImageAltText;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorProfileDtos.Profile;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorProfileDtos.ProfileText;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorProfileDtos.ProfileWrite;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorProfileDtos.PublicDoctor;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorProfileDtos.PublicDoctorDetail;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorProfileDtos.ServiceDoctorsWrite;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorProfileDtos.Versioned;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorRequest;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorResponse;
import com.sokhasaclinic.clinicbackend.doctor.model.DoctorStatus;
import com.sokhasaclinic.clinicbackend.doctor.service.DoctorProfileService;
import com.sokhasaclinic.clinicbackend.doctor.service.DoctorService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Doctor profiles, photos and service links against a real PostgreSQL database (PC-04 to PC-08,
 * DP-01 to DP-11), with R2 replaced by a mock. Set CLINIC_TEST_DATABASE_URL (and _USERNAME /
 * _PASSWORD) to a disposable database to enable it. Each test rolls back.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@EnabledIfEnvironmentVariable(named = "CLINIC_TEST_DATABASE_URL", matches = "jdbc:postgresql:.+")
@Transactional
class DoctorProfilePostgresTests {

    private static final List<ImageAltText> ALT_TEXTS = List.of(
            new ImageAltText("km", "រូបថតវេជ្ជបណ្ឌិត"),
            new ImageAltText("en", "Dr. Sok in a white coat"),
            new ImageAltText("zh", "穿白大褂的医生"));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CLINIC_TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("CLINIC_TEST_DATABASE_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("CLINIC_TEST_DATABASE_PASSWORD"));
    }

    @MockitoBean
    MediaStorage mediaStorage;

    @Autowired DoctorService doctors;
    @Autowired DoctorProfileService profiles;
    @Autowired JdbcTemplate jdbc;

    private String id;
    private String slug;

    @BeforeEach
    void createDoctor() {
        reset(mediaStorage);
        when(mediaStorage.signedUrl(anyString())).thenAnswer(call -> "https://r2.test/" + call.getArgument(0));
        DoctorResponse doctor = doctors.create(new DoctorRequest("Dr. Sok", DoctorStatus.ACTIVE, 0));
        id = doctor.id().toString();
        slug = "dr-sok-" + doctor.id().toString().substring(0, 8);
    }

    @Test
    void newDoctorHasAnEmptyProfileAndIsNotPublic() {
        Profile profile = profiles.profile(id).data();
        assertNull(profile.slug());
        assertTrue(profile.translations().isEmpty());
        assertNull(profile.imageUrl());
        assertFalse(isPublic(Language.EN));
    }

    @Test
    void savedProfileIsPublicWithEveryCardFieldInTheRequestedLanguage() {
        Versioned<Profile> saved = profiles.updateProfile(id, "\"0\"", write(2010, 12));
        assertEquals(1, saved.version());
        assertEquals(slug, saved.data().slug());
        assertEquals(List.of("km", "en", "zh"), saved.data().translations().stream().map(ProfileText::languageCode).toList());

        PublicDoctor en = find(Language.EN);
        assertEquals("Dr. Sok", en.fullName());
        assertEquals("Gastroenterologist", en.title());
        assertEquals("MD, University of Health Sciences", en.education());
        assertEquals(List.of("Endoscopy", "Digestive health"), en.tags());
        assertEquals(Year.now(ClinicTime.ZONE).getValue() - 2010, en.experienceYears());
        assertEquals(12, en.publicationsCount());
        assertNull(en.imageUrl());
        assertEquals(List.of(), en.serviceSlugs());

        PublicDoctor km = find(Language.KM);
        assertEquals("គ្រូពេទ្យឯកទេសរំលាយអាហារ", km.title());
        assertEquals(List.of("ការឆ្លុះ"), km.tags());

        PublicDoctor zh = find(Language.ZH);
        assertNull(zh.bio());
        assertEquals(List.of(), zh.tags());
    }

    @Test
    void unsetStartYearAndCountAreNull() {
        profiles.updateProfile(id, "\"0\"", write(null, null));
        PublicDoctor en = find(Language.EN);
        assertNull(en.experienceYears());
        assertNull(en.publicationsCount());
    }

    @Test
    void invalidProfilesAreRejected() {
        int nextYear = Year.now(ClinicTime.ZONE).getValue() + 1;
        assertEquals(400, status(() -> profiles.updateProfile(id, "\"0\"", write(nextYear, 1))));
        assertEquals(400, status(() -> profiles.updateProfile(id, "\"0\"", write(1949, 1))));
        assertEquals(400, status(() -> profiles.updateProfile(id, "\"0\"", write(2010, 10_001))));
        assertEquals(400, status(() -> profiles.updateProfile(id, "\"0\"",
                new ProfileWrite("Bad Slug", null, null, texts(List.of())))));
        assertEquals(400, status(() -> profiles.updateProfile(id, "\"0\"",
                new ProfileWrite(slug, null, null, texts(List.of()).subList(0, 2)))));
        assertEquals(400, status(() -> profiles.updateProfile(id, "\"0\"",
                new ProfileWrite(slug, null, null, texts(List.of("a", "b", "c", "d", "e", "f"))))));
        assertEquals(400, status(() -> profiles.updateProfile(id, "\"0\"",
                new ProfileWrite(slug, null, null, texts(List.of("Endoscopy", "Endoscopy"))))));
        assertEquals(400, status(() -> profiles.updateProfile(id, "\"0\"",
                new ProfileWrite(slug, null, null, texts(List.of("x".repeat(41)))))));
        // Alt text needs a photo first (DP-08).
        List<ProfileText> withAlt = texts(List.of()).stream()
                .map(t -> new ProfileText(t.languageCode(), t.title(), t.bio(), t.education(), t.tags(), "Alt"))
                .toList();
        assertEquals(400, status(() -> profiles.updateProfile(id, "\"0\"", new ProfileWrite(slug, null, null, withAlt))));
    }

    @Test
    void staleOrMissingIfMatchIsRejected() {
        assertEquals(412, status(() -> profiles.updateProfile(id, "\"3\"", write(2010, 1))));
        assertEquals(428, status(() -> profiles.updateProfile(id, null, write(2010, 1))));
    }

    @Test
    void slugMustBeUnique() {
        profiles.updateProfile(id, "\"0\"", write(2010, 1));
        String other = doctors.create(new DoctorRequest("Dr. Other", DoctorStatus.ACTIVE, 1)).id().toString();
        CmsException taken = assertThrows(CmsException.class, () -> profiles.updateProfile(other, "\"0\"", write(2010, 1)));
        assertEquals("DOCTOR_SLUG_TAKEN", taken.getType());
    }

    @Test
    void inactiveDoctorIsNotPublic() {
        profiles.updateProfile(id, "\"0\"", write(2010, 1));
        doctors.update(UUID.fromString(id), "\"1\"", new DoctorRequest("Dr. Sok", DoctorStatus.INACTIVE, 0));
        assertFalse(isPublic(Language.EN));
        assertEquals("DOCTOR_NOT_FOUND",
                assertThrows(CmsException.class, () -> profiles.publicDoctor(slug, Language.EN)).getType());
    }

    @Test
    void photoNeedsAProfileInEveryLanguage() throws Exception {
        CmsException missing = assertThrows(CmsException.class,
                () -> profiles.replaceImage(id, "\"0\"", png(), ALT_TEXTS));
        assertEquals("TRANSLATION_MISSING", missing.getType());
        assertNull(objectKey());
    }

    @Test
    void photoUploadAltTextEditAndRemoval() throws Exception {
        profiles.updateProfile(id, "\"0\"", write(2010, 1));
        MockMultipartFile photo = png();
        assertEquals(400, status(() -> profiles.replaceImage(id, "\"1\"", photo, ALT_TEXTS.subList(0, 2))));

        Versioned<Profile> withPhoto = profiles.replaceImage(id, "\"1\"", png(), ALT_TEXTS);
        assertEquals(2, withPhoto.version());
        assertEquals("https://r2.test/" + objectKey(), withPhoto.data().imageUrl());
        assertEquals("穿白大褂的医生", find(Language.ZH).imageAltText());
        assertEquals("https://r2.test/" + objectKey(), find(Language.EN).imageUrl());

        // Change only the km alt text through the profile PUT (DP-08).
        List<ProfileText> kmAlt = texts(List.of()).stream()
                .map(t -> new ProfileText(t.languageCode(), t.title(), t.bio(), t.education(), t.tags(),
                        t.languageCode().equals("km") ? "រូបថតថ្មី" : null))
                .toList();
        profiles.updateProfile(id, "\"2\"", new ProfileWrite(slug, 2010, 1, kmAlt));
        assertEquals("រូបថតថ្មី", find(Language.KM).imageAltText());
        assertEquals("Dr. Sok in a white coat", find(Language.EN).imageAltText());

        Versioned<Profile> removed = profiles.removeImage(id, "\"3\"");
        assertNull(removed.data().imageUrl());
        assertTrue(removed.data().translations().stream().allMatch(t -> t.imageAltText() == null));
        assertNull(find(Language.EN).imageUrl());
        assertNull(objectKey());
    }

    @Test
    void linkedServicesAppearOnThePublicProfile() {
        profiles.updateProfile(id, "\"0\"", write(2010, 1));
        Map<String, Object> service = jdbc.queryForMap("""
                select s.id, s.slug, s.version from clinic.service s
                where s.status = 'ACTIVE' order by s.sort_order, s.slug limit 1
                """);
        String serviceId = service.get("id").toString();
        long version = ((Number) service.get("version")).longValue();

        var linked = profiles.replaceServiceDoctors(serviceId, "\"" + version + "\"",
                new ServiceDoctorsWrite(List.of(UUID.fromString(id))));
        assertEquals(version + 1, linked.version());
        assertEquals(List.of(UUID.fromString(id)), linked.data().doctorIds());

        assertEquals(List.of(service.get("slug")), find(Language.EN).serviceSlugs());
        PublicDoctorDetail detail = profiles.publicDoctor(slug, Language.EN);
        assertEquals(1, detail.services().size());
        assertEquals(service.get("slug"), detail.services().getFirst().slug());
        assertEquals(List.of(UUID.fromString(serviceId)), profiles.profile(id).data().serviceIds());

        String next = "\"" + (version + 1) + "\"";
        assertEquals(400, status(() -> profiles.replaceServiceDoctors(serviceId, next,
                new ServiceDoctorsWrite(List.of(UUID.fromString(id), UUID.fromString(id))))));
        assertEquals("DOCTOR_NOT_FOUND", assertThrows(CmsException.class, () -> profiles.replaceServiceDoctors(serviceId,
                next, new ServiceDoctorsWrite(List.of(UUID.randomUUID())))).getType());
        assertEquals(412, status(() -> profiles.replaceServiceDoctors(serviceId, "\"" + version + "\"",
                new ServiceDoctorsWrite(List.of()))));

        profiles.replaceServiceDoctors(serviceId, next, new ServiceDoctorsWrite(List.of()));
        assertEquals(List.of(), find(Language.EN).serviceSlugs());
    }

    private ProfileWrite write(Integer startYear, Integer publications) {
        return new ProfileWrite(slug, startYear, publications, texts(null));
    }

    private static List<ProfileText> texts(List<String> enTags) {
        return List.of(
                new ProfileText("km", "គ្រូពេទ្យឯកទេសរំលាយអាហារ", "ប្រវត្តិរូប", "វេជ្ជបណ្ឌិត", List.of("ការឆ្លុះ"), null),
                new ProfileText("en", "Gastroenterologist", "Dr. Sok has years of experience.",
                        "MD, University of Health Sciences",
                        enTags == null ? List.of("Endoscopy", "Digestive health") : enTags, null),
                new ProfileText("zh", "消化内科医生", "  ", null, null, null));
    }

    private PublicDoctor find(Language language) {
        return profiles.publicDoctors(language).stream().filter(d -> d.slug().equals(slug)).findFirst().orElseThrow();
    }

    private boolean isPublic(Language language) {
        return profiles.publicDoctors(language).stream().anyMatch(d -> slug.equals(d.slug()));
    }

    private String objectKey() {
        return jdbc.query("select m.object_key from clinic.doctor d join clinic.cms_media m on m.id = d.image_media_id "
                + "where d.id = ?::uuid", rs -> rs.next() ? rs.getString(1) : null, id);
    }

    private static int status(Runnable action) {
        return assertThrows(CmsException.class, action::run).getStatus();
    }

    private static MockMultipartFile png() throws Exception {
        BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return new MockMultipartFile("image", "doctor.png", "image/png", bytes.toByteArray());
    }
}
