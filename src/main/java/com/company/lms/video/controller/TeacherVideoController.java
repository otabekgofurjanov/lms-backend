package com.company.lms.video.controller;

import com.company.lms.common.dto.ApiResponse;
import com.company.lms.video.dto.VideoAccessResponse;
import com.company.lms.video.service.VideoAccessService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/teacher/lessons")
@RequiredArgsConstructor
public class TeacherVideoController {
    private final VideoAccessService videoAccessService;

    @GetMapping("/{lessonId}/video")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<VideoAccessResponse> getVideo(@PathVariable UUID lessonId, Authentication authentication, HttpServletRequest request) {
        return ApiResponse.ok(videoAccessService.teacherVideo(lessonId, authentication.getName()), request.getHeader("X-Request-Id"));
    }
}
