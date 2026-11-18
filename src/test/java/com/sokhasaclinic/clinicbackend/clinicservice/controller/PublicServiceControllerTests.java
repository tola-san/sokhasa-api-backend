package com.sokhasaclinic.clinicbackend.clinicservice.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sokhasaclinic.clinicbackend.clinicservice.dto.PublicServiceResponse;
import com.sokhasaclinic.clinicbackend.clinicservice.service.ServiceCatalogService;
import com.sokhasaclinic.clinicbackend.cms.exception.CmsExceptionHandler;
import com.sokhasaclinic.clinicbackend.common.i18n.Language;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PublicServiceControllerTests {

    private static final String SERVICES = "/api/v1/public/services";

    private ServiceCatalogService catalog;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        catalog = mock(ServiceCatalogService.class);
        mvc = MockMvcBuilders.standaloneSetup(new PublicServiceController(catalog))
                .setControllerAdvice(new CmsExceptionHandler())
                .build();
    }

    @Test
    void listsServicesInOneLanguageWithoutCaching() throws Exception {
        UUID id = UUID.fromString("3f1c2a4e-8b7d-4c1a-9e2f-5d6b7a8c9d01");
        when(catalog.listPublicServices(Language.KM)).thenReturn(List.of(new PublicServiceResponse(
                id, "executive-wellness-checkup", "health-checkup-wellness", "ការពិនិត្យសុខភាព",
                "ការពិនិត្យសុខភាពប្រចាំឆ្នាំ", "ការពិនិត្យសុខភាពពេញលេញ។", 120,
                new BigDecimal("155.00"), new BigDecimal("199.00"), null, null)));

        mvc.perform(get(SERVICES).param("lang", "km"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Language", "km"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data[0].id").value(id.toString()))
                .andExpect(jsonPath("$.data[0].slug").value("executive-wellness-checkup"))
                .andExpect(jsonPath("$.data[0].categorySlug").value("health-checkup-wellness"))
                .andExpect(jsonPath("$.data[0].name").value("ការពិនិត្យសុខភាពប្រចាំឆ្នាំ"))
                .andExpect(jsonPath("$.data[0].durationMinutes").value(120))
                .andExpect(jsonPath("$.data[0].priceUsd").value(155.00))
                .andExpect(jsonPath("$.data[0].originalPriceUsd").value(199.00))
                // Optional values are present as null, never omitted. (jsonPath().exists() treats
                // null as missing, so check the raw body.)
                .andExpect(content().string(containsString("\"imageUrl\":null")))
                .andExpect(content().string(containsString("\"imageAltText\":null")))
                // Admin-only fields are never public (SV-07).
                .andExpect(jsonPath("$.data[0].bufferMinutes").doesNotExist())
                .andExpect(jsonPath("$.data[0].status").doesNotExist())
                .andExpect(jsonPath("$.data[0].sortOrder").doesNotExist());
    }

    @Test
    void emptyCatalogueIsOkWithEmptyArray() throws Exception {
        when(catalog.listPublicServices(Language.EN)).thenReturn(List.of());

        mvc.perform(get(SERVICES).param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"fr", "KM", "en-US", ""})
    void unsupportedLanguageIsValidationError(String lang) throws Exception {
        mvc.perform(get(SERVICES).param("lang", lang))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.instance").value(SERVICES));
        verifyNoInteractions(catalog);
    }

    @Test
    void missingLanguageIsValidationError() throws Exception {
        mvc.perform(get(SERVICES))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("VALIDATION_ERROR"));
        verifyNoInteractions(catalog);
    }
}
