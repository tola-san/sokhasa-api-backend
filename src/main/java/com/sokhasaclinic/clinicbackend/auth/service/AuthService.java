package com.sokhasaclinic.clinicbackend.auth.service;

import com.sokhasaclinic.clinicbackend.auth.dto.AuthPermissionsResponse;
import com.sokhasaclinic.clinicbackend.auth.dto.AuthUserResponse;
import com.sokhasaclinic.clinicbackend.staff.model.Staff;
import com.sokhasaclinic.clinicbackend.staff.repository.StaffRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final StaffRepository staffRepository;

    public AuthService(StaffRepository staffRepository) {
        this.staffRepository = staffRepository;
    }

    public AuthUserResponse getMe(Jwt jwt) {
        String keycloakUserId = jwt.getSubject();
        String username = jwt.getClaimAsString("preferred_username");
        if (username == null || username.isBlank()) {
            username = jwt.getSubject();
        }
        String email = jwt.getClaimAsString("email");
        List<String> roles = extractRoles(jwt);

        Optional<Staff> staffOpt = staffRepository.findByKeycloakUserId(keycloakUserId);
        UUID id = staffOpt.map(Staff::getId).orElse(null);
        boolean active = staffOpt.map(Staff::isActive).orElse(true);

        return new AuthUserResponse(
                id,
                keycloakUserId,
                username,
                email,
                roles,
                active
        );
    }

    public AuthPermissionsResponse getPermissions(Jwt jwt) {
        List<String> roles = extractRoles(jwt);
        List<String> permissions = new ArrayList<>();

        if (roles.contains("RECEPTION")) {
            permissions.addAll(List.of(
                    "appointment:read",
                    "appointment:confirm",
                    "appointment:reject",
                    "appointment:reschedule",
                    "appointment:assign-doctor",
                    "dashboard:read"
            ));
        }

        if (roles.contains("ADMIN")) {
            permissions.addAll(List.of(
                    "appointment:read",
                    "appointment:confirm",
                    "appointment:reject",
                    "appointment:reschedule",
                    "appointment:assign-doctor",
                    "doctor:read",
                    "doctor:write",
                    "service:read",
                    "service:write",
                    "page:read",
                    "page:write",
                    "media:read",
                    "media:write",
                    "staff:read",
                    "staff:write",
                    "settings:read",
                    "settings:write",
                    "dashboard:read"
            ));
        }

        return new AuthPermissionsResponse(
                roles,
                permissions.stream().distinct().toList()
        );
    }

    private List<String> extractRoles(Jwt jwt) {
        if (!(jwt.getClaims().get("realm_access") instanceof Map<?, ?> realmAccess)
                || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }

        return roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(r -> "ADMIN".equals(r) || "RECEPTION".equals(r))
                .distinct()
                .toList();
    }
}
