package com.sokhasaclinic.clinicbackend.auth.controller;

import com.sokhasaclinic.clinicbackend.auth.dto.AuthPermissionsResponse;
import com.sokhasaclinic.clinicbackend.auth.dto.AuthUserResponse;
import com.sokhasaclinic.clinicbackend.auth.service.AuthService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    public AuthUserResponse getMe(@AuthenticationPrincipal Jwt jwt) {
        return authService.getMe(jwt);
    }

    @GetMapping("/permissions")
    public AuthPermissionsResponse getPermissions(@AuthenticationPrincipal Jwt jwt) {
        return authService.getPermissions(jwt);
    }
}
