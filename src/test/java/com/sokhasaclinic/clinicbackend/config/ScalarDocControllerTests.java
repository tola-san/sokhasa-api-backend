package com.sokhasaclinic.clinicbackend.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ScalarDocControllerTests {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ScalarDocController controller = new ScalarDocController();
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/v1/openapi.yaml should return 200 with YAML specification")
    void getOpenApiYamlReturns200() throws Exception {
        mockMvc.perform(get("/api/v1/openapi.yaml"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/yaml"));
    }

    @Test
    @DisplayName("GET /scalar should return 200 with Scalar HTML documentation UI")
    void getScalarUiReturns200() throws Exception {
        mockMvc.perform(get("/scalar"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("@scalar/api-reference")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-url=\"/api/v1/openapi.yaml\"")));
    }

    @Test
    @DisplayName("GET /docs should return 200 with Scalar HTML documentation UI")
    void getDocsUiReturns200() throws Exception {
        mockMvc.perform(get("/docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("@scalar/api-reference")));
    }
}
