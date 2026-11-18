package com.sokhasaclinic.clinicbackend.staff.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record StaffRequest(
        @NotBlank(message = "Username is required.")
        @Size(max = 100, message = "Username must be at most 100 characters.")
        String username,

        @Email(message = "Email must be valid.")
        @Size(max = 255, message = "Email must be at most 255 characters.")
        String email,

        @Size(max = 100, message = "First name must be at most 100 characters.")
        String firstName,

        @Size(max = 100, message = "Last name must be at most 100 characters.")
        String lastName,

        @NotEmpty(message = "At least one role is required.")
        List<String> roles
) {}
