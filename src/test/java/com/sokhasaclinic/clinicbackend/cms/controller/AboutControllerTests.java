package com.sokhasaclinic.clinicbackend.cms.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.sokhasaclinic.clinicbackend.cms.dto.AboutDtos.*;
import com.sokhasaclinic.clinicbackend.cms.exception.CmsExceptionHandler;
import com.sokhasaclinic.clinicbackend.cms.service.AboutService;
import com.sokhasaclinic.clinicbackend.common.i18n.Language;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AboutControllerTests {
    AboutService service;
    MockMvc mvc;
    @BeforeEach void setup() {
        service=mock(AboutService.class);
        mvc=MockMvcBuilders.standaloneSetup(new AboutController(service)).setControllerAdvice(new CmsExceptionHandler()).build();
    }
    @Test void requiresExplicitSupportedLanguage() throws Exception {
        for (String value : List.of("","fr","KM")) mvc.perform(get("/api/v1/public/about").param("lang",value))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.type").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("lang"));
        mvc.perform(get("/api/v1/public/about")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void publicResponseIsUncachedAndKeepsNulls() throws Exception {
        when(service.publicPage(Language.ZH)).thenReturn(new PublicPage("标题","介绍","故事",List.of("一段"),
                List.of(),List.of(),List.of(),List.of(new PublicFacility("设施","描述",null,null,null))));
        mvc.perform(get("/api/v1/public/about").param("lang","zh")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("Content-Language","zh"))
                .andExpect(jsonPath("$.data.heroTitle").value("标题"))
                .andExpect(jsonPath("$.data.facilities[0].imageUrl").isEmpty());
    }
    private Versioned admin() {
        return new Versioned(new Admin(4,List.of(),List.of(),List.of(),
                List.of(new Founder(UUID.randomUUID(),"Founder",true)),List.of(),Instant.now()),4);
    }
    @Test void adminUsesStrongEtagAndContractFounderField() throws Exception {
        when(service.admin()).thenReturn(admin());
        mvc.perform(get("/api/v1/admin/about")).andExpect(status().isOk()).andExpect(header().string("ETag","\"4\""))
                .andExpect(jsonPath("$.data.founders[0].public").value(true));
    }
    @Test void uploadReadsJsonAltTextsAndUsesPageVersion() throws Exception {
        UUID id=UUID.randomUUID();
        when(service.replaceImage(eq(id),eq("\"3\""),any(),any())).thenReturn(admin());
        mvc.perform(multipart(HttpMethod.PUT,"/api/v1/admin/about/facilities/"+id+"/image")
                .file(new MockMultipartFile("image","clinic.png","image/png",new byte[]{1}))
                .file(new MockMultipartFile("altTexts","","application/json","[{\"languageCode\":\"en\",\"imageAltText\":\"Clinic\"}]".getBytes()))
                .header("If-Match","\"3\""))
                .andExpect(status().isOk()).andExpect(header().string("ETag","\"4\""));
        verify(service).replaceImage(eq(id),eq("\"3\""),any(),eq(List.of(new AltText("en","Clinic"))));
    }
}
