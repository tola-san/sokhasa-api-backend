package com.sokhasaclinic.clinicbackend.config;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.DispatcherType;

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
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;




@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private static final Set<String> STAFF_ROLES = Set.of("ADMIN", "RECEPTION");

    /**
     * Read-only routes anyone may call. Keep this list short and explicit: a route that is not
     * listed here or matched by a role rule below is denied.
     */
    static final String[] PUBLIC_GET_ROUTES = {
            "/api/v1/public/**",
            "/api/v1/clinic/info",
            "/api/v1/health",
            "/api/v1/openapi.yaml",
            "/api/v1/docs",
            "/docs",
            "/scalar",
    };

    /** Admin routes RECEPTION may use too; every other {@code /api/v1/admin/**} route stays ADMIN only. */
    static final String[] RECEPTION_ROUTES = {
            "/api/v1/admin/appointments",
            "/api/v1/admin/appointments/**",
            "/api/v1/admin/dashboard/summary",
            "/api/v1/admin/doctors",
    };

    /**
     * The only filter chain for the application. It has no security matcher, so it covers every
     * request, and it denies by default: a new controller stays unreachable until a rule here
     * grants access to it.
     */
    @Bean
    SecurityFilterChain apiSecurity(
            HttpSecurity http,
            ObjectProvider<JwtDecoder> jwtDecoder,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri)
            throws Exception {
        http.authorizeHttpRequests(requests -> requests
                        // Let the container render errors (for example a 404 from a public route)
                        // instead of turning them into 401.
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_ROUTES).permitAll()
                        // Guests submit booking requests (appointment contract, approved November 18, 2026).
                        .requestMatchers(HttpMethod.POST, "/api/v1/public/appointments").permitAll()
                        // Telegram's bot updates (PT-03): no Keycloak token; the controller checks
                        // Telegram's secret header instead.
                        .requestMatchers(HttpMethod.POST, "/api/v1/telegram/webhook").permitAll()
                        // The existing active-doctor GET is shared; creating resources is admin only.
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/doctors").hasRole("ADMIN")
                        // Reception works appointments as well as admins (AP-07).
                        .requestMatchers(RECEPTION_ROUTES).hasAnyRole("ADMIN", "RECEPTION")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // Add new role rules above this line, most specific first.
                        .anyRequest().denyAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Callers authenticate with the Authorization header, not cookies, so CSRF protection is unnecessary.
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                        .accessDeniedHandler(new BearerTokenAccessDeniedHandler()));

        // With no issuer configured, the app can start and protected requests still require authentication.
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
