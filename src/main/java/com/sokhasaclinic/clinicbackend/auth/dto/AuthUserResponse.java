package com.sokhasaclinic.clinicbackend.auth.dto;

import java.util.List;
import java.util.UUID;

public record AuthUserResponse(
        UUID id,
        String keycloakUserId,
        String username,
        String email,
        List<String> roles,
        boolean active
) {}
