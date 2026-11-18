package com.sokhasaclinic.clinicbackend.auth.dto;

import java.util.List;

public record AuthPermissionsResponse(
        List<String> roles,
        List<String> permissions
) {}
