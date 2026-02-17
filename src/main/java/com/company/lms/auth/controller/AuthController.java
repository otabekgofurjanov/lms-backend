package com.company.lms.auth.controller;

import com.company.lms.auth.dto.AuthTokensDto;
import com.company.lms.auth.dto.LoginRequest;
import com.company.lms.auth.dto.MeResponse;
import com.company.lms.auth.dto.RefreshRequest;
import com.company.lms.auth.service.AuthService;
import com.company.lms.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<AuthTokensDto> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpServletRequest) {
        return ApiResponse.ok(authService.login(request, httpServletRequest), httpServletRequest.getHeader("X-Request-Id"));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthTokensDto> refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest httpServletRequest) {
        return ApiResponse.ok(authService.refresh(request), httpServletRequest.getHeader("X-Request-Id"));
    }

    @PostMapping("/logout")
    public ApiResponse<String> logout(@Valid @RequestBody RefreshRequest request, HttpServletRequest httpServletRequest) {
        authService.logout(request, httpServletRequest);
        return ApiResponse.ok("logged out", httpServletRequest.getHeader("X-Request-Id"));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<MeResponse> me(HttpServletRequest httpServletRequest) {
        return ApiResponse.ok(authService.me(), httpServletRequest.getHeader("X-Request-Id"));
    }
}
