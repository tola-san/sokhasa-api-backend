package com.sokhasaclinic.clinicbackend.doctor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sokhasaclinic.clinicbackend.cms.exception.CmsException;
import com.sokhasaclinic.clinicbackend.cms.exception.CmsExceptionHandler;
import com.sokhasaclinic.clinicbackend.common.i18n.Language;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorProfileDtos.Profile;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorProfileDtos.ProfileText;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorProfileDtos.PublicDoctor;
import com.sokhasaclinic.clinicbackend.doctor.dto.DoctorProfileDtos.Versioned;
import com.sokhasaclinic.clinicbackend.doctor.profile.AdminDoctorProfileController;
import com.sokhasaclinic.clinicbackend.doctor.profile.PublicDoctorController;
import com.sokhasaclinic.clinicbackend.doctor.service.DoctorProfileService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DoctorProfileControllerTests {

    DoctorProfileService service;
    MockMvc mvc;
    UUID id = UUID.randomUUID();

    @BeforeEach
    void setup() {
        service = mock(DoctorProfileService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AdminDoctorProfileController(service), new PublicDoctorController(service))
                .setControllerAdvice(new CmsExceptionHandler()).build();
    }

    @Test
    void publicListIsLocalizedUncachedAndHasEveryCardField() throws Exception {
        when(service.publicDoctors(Language.KM)).thenReturn(List.of(new PublicDoctor("dr-sok", "Dr. Sok", "គ្រូពេទ្យ",
                null, null, List.of("ការឆ្លុះ"), 16, null, null, null, List.of("colonoscopy"))));
        mvc.perform(get("/api/v1/public/doctors").param("lang", "km"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Language", "km"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data[0].title").value("គ្រូពេទ្យ"))
                .andExpect(jsonPath("$.data[0].experienceYears").value(16))
                .andExpect(jsonPath("$.data[0].bio").isEmpty())
                .andExpect(jsonPath("$.data[0].publicationsCount").isEmpty())
                .andExpect(jsonPath("$.data[0].tags[0]").value("ការឆ្លុះ"))
                .andExpect(jsonPath("$.data[0].id").doesNotExist());
    }

    @Test
    void badLanguageAndUnknownSlugReturnProblems() throws Exception {
        mvc.perform(get("/api/v1/public/doctors").param("lang", "fr"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.type").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/v1/public/doctors"))
                .andExpect(status().isBadRequest());
        when(service.publicDoctor("nobody", Language.EN)).thenThrow(CmsException.doctorNotFound());
        mvc.perform(get("/api/v1/public/doctors/nobody").param("lang", "en"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.type").value("DOCTOR_NOT_FOUND"));
    }

    @Test
    void profileWritesReturnTheDoctorVersionAsEtag() throws Exception {
        Profile profile = new Profile(id, "dr-sok", 2010, 12, null,
                List.of(new ProfileText("en", "Gastroenterologist", null, null, List.of(), null)), List.of(), 4);
        when(service.updateProfile(eq(id.toString()), eq("\"3\""), any())).thenReturn(new Versioned<>(profile, 4));
        mvc.perform(put("/api/v1/admin/doctors/" + id + "/profile").header("If-Match", "\"3\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"dr-sok\",\"practiceStartYear\":2010,\"publicationsCount\":12,\"translations\":[]}"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"4\""))
                .andExpect(jsonPath("$.data.practiceStartYear").value(2010))
                .andExpect(jsonPath("$.data.translations[0].title").value("Gastroenterologist"));
    }

    @Test
    void photoUploadTakesAJsonAltTextsPart() throws Exception {
        Profile profile = new Profile(id, "dr-sok", null, null, "https://r2.test/media/x", List.of(), List.of(), 2);
        when(service.replaceImage(eq(id.toString()), eq("\"1\""), any(), anyList())).thenReturn(new Versioned<>(profile, 2));
        MockMultipartFile image = new MockMultipartFile("image", "d.png", "image/png", new byte[] {1});
        MockMultipartFile alts = new MockMultipartFile("altTexts", "", "application/json",
                "[{\"languageCode\":\"en\",\"imageAltText\":\"Dr. Sok\"}]".getBytes());
        mvc.perform(multipart("/api/v1/admin/doctors/" + id + "/image").file(image).file(alts)
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .header("If-Match", "\"1\""))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.data.imageUrl").value("https://r2.test/media/x"));
    }

    @Test
    void malformedBodyIsAValidationError() throws Exception {
        mvc.perform(put("/api/v1/admin/services/" + id + "/doctors").header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.type").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }
}
