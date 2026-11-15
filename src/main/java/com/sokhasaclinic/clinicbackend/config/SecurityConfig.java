package com.sokhasaclinic.clinicbackend.config;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
    SecurityFilterChain cmsAdminSecurity(
            HttpSecurity http,
            ObjectProvider<JwtDecoder> jwtDecoder,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri)

            throws Exception {
        // Apply this filter chain to CMS admin paths. Public routes sit outside it.
        http.securityMatcher("/api/v1/admin/pages", "/api/v1/admin/pages/**", "/api/v1/admin/media/upload")
                .authorizeHttpRequests(requests -> requests.anyRequest().hasRole("ADMIN"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // These paths authenticate through the Authorization header, so CSRF protection is unnecessary here.
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors.authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint()));

        // With no issuer configured, the app can start and CMS admin requests still require authentication.
        if (StringUtils.hasText(issuerUri)) {
            JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
            converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::staffAuthorities);
            http.oauth2ResourceServer(server -> server.jwt(jwt -> jwt
                    .decoder(jwtDecoder.getObject())
                    .jwtAuthenticationConverter(converter)));
        }

        return http.build();
    }

    private static Collection<GrantedAuthority> staffAuthorities(Jwt jwt) {
        // Keycloak puts realm roles in realm_access.roles. Spring's hasRole expects the ROLE_ prefix.
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
