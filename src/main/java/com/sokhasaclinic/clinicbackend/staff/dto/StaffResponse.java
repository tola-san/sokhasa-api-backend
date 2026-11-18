package com.sokhasaclinic.clinicbackend.staff.dto;

import com.sokhasaclinic.clinicbackend.staff.model.Staff;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public record StaffResponse(
        UUID id,
        String keycloakUserId,
        String username,
        String email,
        String firstName,
        String lastName,
        List<String> roles,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static StaffResponse fromEntity(Staff staff) {
        if (staff == null) return null;
        List<String> rolesList = staff.getRoles() != null
                ? Arrays.asList(staff.getRoles())
                : List.of();

        return new StaffResponse(
                staff.getId(),
                staff.getKeycloakUserId(),
                staff.getUsername(),
                staff.getEmail(),
                staff.getFirstName(),
                staff.getLastName(),
                rolesList,
                staff.isActive(),
                staff.getCreatedAt(),
                staff.getUpdatedAt()
        );
    }
}
