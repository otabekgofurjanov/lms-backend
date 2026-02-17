package com.company.lms.video.controller;

import com.company.lms.common.dto.ApiResponse;
import com.company.lms.video.dto.*;
import com.company.lms.video.service.VideoProgressService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/student/lessons")
@RequiredArgsConstructor
public class StudentVideoProgressController {
    private final VideoProgressService videoProgressService;

    @PostMapping("/{lessonId}/video/session")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<VideoSessionResponse> createSession(@PathVariable UUID lessonId, Authentication authentication, HttpServletRequest request) {
        return ApiResponse.ok(videoProgressService.createSession(lessonId, authentication.getName()), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/{lessonId}/video/progress")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<VideoProgressUpdateResponse> updateProgress(@PathVariable UUID lessonId,
                                                                   @Valid @RequestBody VideoProgressEventRequest body,
                                                                   Authentication authentication,
                                                                   HttpServletRequest request) {
        return ApiResponse.ok(videoProgressService.acceptProgress(lessonId, authentication.getName(), body), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/{lessonId}/video/progress")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<VideoProgressSnapshotResponse> getProgress(@PathVariable UUID lessonId, Authentication authentication, HttpServletRequest request) {
        return ApiResponse.ok(videoProgressService.studentProgress(lessonId, authentication.getName()), request.getHeader("X-Request-Id"));
    }
}
