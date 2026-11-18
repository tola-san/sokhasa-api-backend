package com.sokhasaclinic.clinicbackend.clinicservice.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sokhasaclinic.clinicbackend.clinicservice.dto.AdminServiceDtos.AdminService;
import com.sokhasaclinic.clinicbackend.clinicservice.dto.AdminServiceDtos.AdminServiceText;
import com.sokhasaclinic.clinicbackend.clinicservice.dto.AdminServiceDtos.VersionedService;
import com.sokhasaclinic.clinicbackend.clinicservice.model.ServiceStatus;
import com.sokhasaclinic.clinicbackend.clinicservice.service.ServiceAdminService;
import com.sokhasaclinic.clinicbackend.cms.exception.CmsException;
import com.sokhasaclinic.clinicbackend.cms.exception.CmsExceptionHandler;
import com.sokhasaclinic.clinicbackend.common.i18n.Language;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminServiceControllerTests {

    private static final String ID = "e105d306-5252-4b58-ac95-0b1308887001";
    private static final String SERVICES = "/api/v1/admin/services";
    private static final String VALID_BODY = """
            {"durationMinutes":45,"bufferMinutes":10,"priceUsd":40.00,"originalPriceUsd":null,"sortOrder":1,
             "translations":[{"languageCode":"zh","name":"慢性病管理","shortDescription":"说明。"}]}
            """;

    private ServiceAdminService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ServiceAdminService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AdminServiceController(service))
                .setControllerAdvice(new CmsExceptionHandler())
                .build();
    }

    @Test
    void listReturnsEveryServiceWithoutCaching() throws Exception {
        when(service.listServices()).thenReturn(List.of(body(3)));

        mvc.perform(get(SERVICES))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data[0].id").value(ID))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].bufferMinutes").value(5))
                .andExpect(jsonPath("$.data[0].version").value(3))
                .andExpect(jsonPath("$.data[0].translations[0].languageCode").value("km"))
                .andExpect(content().string(containsString("\"originalPriceUsd\":null")))
                .andExpect(content().string(containsString("\"imageAltText\":null")));
    }

    @Test
    void getReturnsVersionAsStrongEtag() throws Exception {
        when(service.getService(ID)).thenReturn(new VersionedService(body(3), 3));

        mvc.perform(get(SERVICES + "/" + ID))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"3\""))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void putPassesIfMatchAndReturnsNewEtag() throws Exception {
        when(service.updateService(eq(ID), eq("\"3\""), any())).thenReturn(new VersionedService(body(4), 4));

        mvc.perform(put(SERVICES + "/" + ID).header("If-Match", "\"3\"")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"4\""))
                .andExpect(jsonPath("$.data.version").value(4));
        verify(service).updateService(eq(ID), eq("\"3\""), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"durationMinutes\":500,\"bufferMinutes\":0,\"priceUsd\":1,\"sortOrder\":0}",
            "{\"durationMinutes\":30,\"bufferMinutes\":61,\"priceUsd\":1,\"sortOrder\":0}",
            "{\"durationMinutes\":30,\"bufferMinutes\":0,\"priceUsd\":-1,\"sortOrder\":0}",
            "{\"durationMinutes\":30,\"bufferMinutes\":0,\"priceUsd\":1.005,\"sortOrder\":0}",
            "{\"bufferMinutes\":0,\"priceUsd\":1,\"sortOrder\":0}",
            "{\"durationMinutes\":30,\"bufferMinutes\":0,\"priceUsd\":1,\"sortOrder\":10000}",
            "not json"
    })
    void invalidBodyIsValidationError(String body) throws Exception {
        mvc.perform(put(SERVICES + "/" + ID).header("If-Match", "\"3\"")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @Test
    void serviceErrorsUseProblemDetails() throws Exception {
        when(service.updateService(eq(ID), eq(null), any())).thenThrow(CmsException.versionRequired());
        when(service.changeStatus(ID, "\"1\"", ServiceStatus.ACTIVE))
                .thenThrow(CmsException.serviceTranslationMissing(List.of(Language.ZH)));
        when(service.getService("nope")).thenThrow(CmsException.serviceNotFound());

        mvc.perform(put(SERVICES + "/" + ID).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.type").value("VERSION_REQUIRED"));
        mvc.perform(patch(SERVICES + "/" + ID + "/activate").header("If-Match", "\"1\""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("TRANSLATION_MISSING"))
                .andExpect(jsonPath("$.errors[0].languageCode").value("zh"));
        mvc.perform(get(SERVICES + "/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("SERVICE_NOT_FOUND"));
    }

    @Test
    void deactivateCallsServiceWithInactive() throws Exception {
        when(service.changeStatus(ID, "\"3\"", ServiceStatus.INACTIVE)).thenReturn(new VersionedService(body(4), 4));

        mvc.perform(patch(SERVICES + "/" + ID + "/deactivate").header("If-Match", "\"3\""))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"4\""));
    }

    private static AdminService body(long version) {
        return new AdminService(UUID.fromString(ID), "chronic-disease-management", "general-internal-medicine",
                "General & Internal Medicine", "ACTIVE", 30, 5, new BigDecimal("35.00"), null, 1, null,
                Instant.parse("2026-11-18T06:49:50Z"), version,
                List.of(new AdminServiceText("km", "ឈ្មោះ", "ការពិពណ៌នា", null)), List.of());
    }
}
