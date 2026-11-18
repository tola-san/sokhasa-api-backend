package com.sokhasaclinic.clinicbackend.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.Filter;
import java.util.List;
import java.util.Map;
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

/**
 * Access rules of the appointment contract (approved November 18, 2026): guests may book, ADMIN and
 * RECEPTION share the reception routes (AP-07), and the rest of /admin stays ADMIN only. No
 * controllers are loaded, so a request that passes security gets 404 from MVC.
 */
@SpringJUnitWebConfig(AppointmentSecurityTests.WebConfig.class)
@TestPropertySource(properties = "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example/realms/clinic")
class AppointmentSecurityTests {

    private static final String APPOINTMENT = "/api/v1/admin/appointments/3f1c2a4e-8b7d-4c1a-9e2f-5d6b7a8c9d01";

    private MockMvc mvc;

    @BeforeEach
    void setUp(@Autowired WebApplicationContext context,
            @Autowired @Qualifier("springSecurityFilterChain") Filter security) {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
    }

    @Test
    void guestsCanBookAndReadPeriods() throws Exception {
        mvc.perform(post("/api/v1/public/appointments")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/public/booking-periods")).andExpect(status().isNotFound());
    }

    @Test
    void guestsCannotUseReceptionRoutes() throws Exception {
        mvc.perform(get("/api/v1/admin/appointments")).andExpect(status().isUnauthorized());
        mvc.perform(patch(APPOINTMENT + "/confirm")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/dashboard/summary")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/doctors")).andExpect(status().isUnauthorized());
    }

    @Test
    void receptionAndAdminShareTheReceptionRoutes() throws Exception {
        for (String token : List.of("receptionist", "administrator")) {
            String bearer = "Bearer " + token;
            mvc.perform(get("/api/v1/admin/appointments").header("Authorization", bearer)).andExpect(status().isNotFound());
            mvc.perform(get(APPOINTMENT).header("Authorization", bearer)).andExpect(status().isNotFound());
            mvc.perform(patch(APPOINTMENT + "/arrive").header("Authorization", bearer)).andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/admin/dashboard/summary").header("Authorization", bearer)).andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/admin/doctors").header("Authorization", bearer)).andExpect(status().isNotFound());
        }
    }

    @Test
    void receptionStillCannotUseOtherAdminRoutes() throws Exception {
        mvc.perform(get("/api/v1/admin/services").header("Authorization", "Bearer receptionist"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/doctors/new").header("Authorization", "Bearer receptionist"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", "Bearer receptionist"))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyBookingIsOpenForPostOnPublicRoutes() throws Exception {
        mvc.perform(post("/api/v1/public/booking-periods")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/public/appointments/123")).andExpect(status().isUnauthorized());
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import(SecurityConfig.class)
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
