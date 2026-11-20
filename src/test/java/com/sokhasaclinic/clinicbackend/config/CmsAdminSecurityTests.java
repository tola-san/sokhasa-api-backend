package com.sokhasaclinic.clinicbackend.config;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import jakarta.servlet.Filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.sokhasaclinic.clinicbackend.clinic.ClinicInfoController;
import com.sokhasaclinic.clinicbackend.clinic.ClinicInfoService;

@SpringJUnitWebConfig(CmsAdminSecurityTests.WebConfig.class)
@TestPropertySource(properties = "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example/realms/clinic")
class CmsAdminSecurityTests {

    private static final String BANNER = "/api/v1/admin/homepage/banner";

    private MockMvc mvc;

    @BeforeEach
    void setUp(@Autowired WebApplicationContext context,
            @Autowired @Qualifier("springSecurityFilterChain") Filter security) {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
    }

    @Test
    void guestCannotAccessAdminRoutes() throws Exception {
        mvc.perform(patch(BANNER)).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", matchesPattern("Bearer(?: .*)?")));
        mvc.perform(get(BANNER)).andExpect(status().isUnauthorized());
        // Any future admin route is protected without changing the security matcher.
        mvc.perform(get("/api/v1/admin/not-yet-built")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTokenIsUnauthorized() throws Exception {
        mvc.perform(get(BANNER).header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void receptionistCannotPublishBanner() throws Exception {
        mvc.perform(patch(BANNER).header("Authorization", "Bearer receptionist"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aboutPageAndFacilityImagesAreAdminOnly() throws Exception {
        String about = "/api/v1/admin/about";
        String image = about + "/facilities/ab100000-0000-4000-8000-000000000001/image";
        mvc.perform(get(about)).andExpect(status().isUnauthorized());
        mvc.perform(get(about).header("Authorization", "Bearer receptionist"))
                .andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(about)
                .header("Authorization", "Bearer receptionist")).andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(image)
                .header("Authorization", "Bearer receptionist")).andExpect(status().isForbidden());
    }

    @Test
    void receptionistCannotViewOrEditServices() throws Exception {
        // Admin service routes are ADMIN only, including reads (decisions SA-01 and SA-09).
        String service = "/api/v1/admin/services/3f1c2a4e-8b7d-4c1a-9e2f-5d6b7a8c9d01";
        mvc.perform(get("/api/v1/admin/services").header("Authorization", "Bearer receptionist"))
                .andExpect(status().isForbidden());
        mvc.perform(get(service).header("Authorization", "Bearer receptionist"))
                .andExpect(status().isForbidden());
        mvc.perform(patch(service + "/deactivate").header("Authorization", "Bearer receptionist"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanPassSecurityFilter() throws Exception {
        mvc.perform(get(BANNER).header("Authorization", "Bearer administrator"))
                .andExpect(status().isNotFound());
    }

    @Test
    void clinicInfoRemainsPublic() throws Exception {
        mvc.perform(get("/api/v1/clinic/info")).andExpect(status().isOk());
    }

    @Test
    void publicBannerRouteIsOpenToGuests() throws Exception {
        // No banner controller is loaded here, so a public route reaches MVC and returns 404, not 401.
        mvc.perform(get("/api/v1/public/homepage/banner")).andExpect(status().isNotFound());
    }

    @Test
    void unlistedRoutesAreDeniedByDefault() throws Exception {
        // A controller added later without a security rule must not become public.
        mvc.perform(get("/api/v1/reception/appointments")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/appointments")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    void unlistedRoutesStayDeniedForSignedInStaff() throws Exception {
        mvc.perform(get("/api/v1/reception/appointments").header("Authorization", "Bearer administrator"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reception/appointments").header("Authorization", "Bearer receptionist"))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicRoutesAreReadOnly() throws Exception {
        mvc.perform(post("/api/v1/public/homepage/banner")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/clinic/info")).andExpect(status().isUnauthorized());
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, ClinicInfoController.class, ClinicInfoService.class})
    static class WebConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                String role = switch (token) {
                    case "administrator" -> "ADMIN";
                    case "receptionist" -> "RECEPTION";
                    default -> throw new BadJwtException("Invalid test token");
                };
                return Jwt.withTokenValue(token)
                        .header("alg", "RS256")
                        .subject(token)
                        .claim("realm_access", Map.of("roles", List.of(role)))
                        .build();
            };
        }
    }
}
