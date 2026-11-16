package com.sokhasaclinic.clinicbackend.config;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    void adminCanPassSecurityFilter() throws Exception {
        mvc.perform(get(BANNER).header("Authorization", "Bearer administrator"))
                .andExpect(status().isNotFound());
    }

    @Test
    void clinicInfoRemainsPublic() throws Exception {
        mvc.perform(get("/api/v1/clinic/info")).andExpect(status().isOk());
    }

    @Test
    void publicBannerRouteIsOutsideAdminChain() throws Exception {
        // No banner controller is loaded here, so a public route reaches MVC and returns 404, not 401.
        mvc.perform(get("/api/v1/public/homepage/banner")).andExpect(status().isNotFound());
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
