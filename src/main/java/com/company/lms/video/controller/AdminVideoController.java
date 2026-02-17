package com.company.lms.video.controller;

import com.company.lms.common.dto.ApiResponse;
import com.company.lms.video.dto.VideoAccessResponse;
import com.company.lms.video.service.VideoAccessService;
import com.company.lms.video.service.VideoAdminService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/lessons")
@RequiredArgsConstructor
public class AdminVideoController {
    private final VideoAccessService videoAccessService;
    private final VideoAdminService videoAdminService;

    @GetMapping("/{lessonId}/video")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<VideoAccessResponse> getVideo(@PathVariable UUID lessonId, Authentication authentication, HttpServletRequest request) {
        return ApiResponse.ok(videoAccessService.adminVideo(lessonId, authentication.getName()), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/{lessonId}/recording/retry")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> retry(@PathVariable UUID lessonId, Authentication authentication, HttpServletRequest request) {
        return ApiResponse.ok(videoAdminService.retry(lessonId, authentication.getName()), request.getHeader("X-Request-Id"));
    }
}
