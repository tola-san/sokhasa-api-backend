package com.sokhasaclinic.clinicbackend.config;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private static final Set<String> STAFF_ROLES = Set.of("ADMIN", "RECEPTION");

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<JwtDecoder> jwtDecoder,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri)
            throws Exception {

        http
                .authorizeHttpRequests(requests -> requests
                        // Public endpoints
                        .requestMatchers(
                                "/api/v1/health",
                                "/api/v1/clinic/info",
                                "/api/v1/public/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        // Endpoints accessible by both ADMIN and RECEPTION
                        .requestMatchers(
                                "/api/v1/auth/me",
                                "/api/v1/auth/permissions",
                                "/api/v1/admin/appointments",
                                "/api/v1/admin/appointments/**",
                                "/api/v1/admin/dashboard",
                                "/api/v1/admin/dashboard/**"
                        ).hasAnyRole("ADMIN", "RECEPTION")

                        // Endpoints accessible only by ADMIN
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                        // Any other request under /api/v1 requires authentication
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors.authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint()));

        if (StringUtils.hasText(issuerUri)) {
            JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
            converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::staffAuthorities);
            http.oauth2ResourceServer(server -> server.jwt(jwt -> jwt
                    .decoder(jwtDecoder.getObject())
                    .jwtAuthenticationConverter(converter)));
        }

        return http.build();
    }

    public static Collection<GrantedAuthority> staffAuthorities(Jwt jwt) {
        if (!(jwt.getClaims().get("realm_access") instanceof Map<?, ?> realmAccess)
                || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }

        return roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(STAFF_ROLES::contains)
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .distinct()
                .toList();
    }
}
