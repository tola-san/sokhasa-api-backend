package com.sokhasaclinic.clinicbackend.staff.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record UpdateStaffRolesRequest(
        @NotEmpty(message = "Roles cannot be empty.")
        List<String> roles
) {}
