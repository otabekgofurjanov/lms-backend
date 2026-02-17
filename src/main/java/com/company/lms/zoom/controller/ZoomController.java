package com.company.lms.zoom.controller;

import com.company.lms.common.dto.ApiResponse;
import com.company.lms.zoom.dto.ZoomConnectUrlResponse;
import com.company.lms.zoom.service.ZoomService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/zoom")
@RequiredArgsConstructor
public class ZoomController {
    private final ZoomService zoomService;

    @GetMapping("/connect-url")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<ZoomConnectUrlResponse> connectUrl(Authentication authentication, HttpServletRequest request) {
        return ApiResponse.ok(zoomService.connectUrl(authentication.getName()), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/oauth/callback")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<String> oauthCallback(@RequestParam String code,
                                             @RequestParam String state,
                                             Authentication authentication,
                                             HttpServletRequest request) {
        return ApiResponse.ok(zoomService.oauthCallback(code, state, authentication.getName(), request), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/disconnect")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<String> disconnect(Authentication authentication, HttpServletRequest request) {
        return ApiResponse.ok(zoomService.disconnect(authentication.getName(), request), request.getHeader("X-Request-Id"));
    }
}
