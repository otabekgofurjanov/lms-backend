package com.company.lms.video.controller;

import com.company.lms.common.dto.ApiResponse;
import com.company.lms.video.dto.CourseVideoProgressReportResponse;
import com.company.lms.video.service.VideoProgressService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/teacher/courses")
@RequiredArgsConstructor
public class TeacherVideoProgressReportController {
    private final VideoProgressService videoProgressService;

    @GetMapping("/{courseId}/video-progress")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<CourseVideoProgressReportResponse> report(@PathVariable UUID courseId,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size,
                                                                 Authentication authentication,
                                                                 HttpServletRequest request) {
        return ApiResponse.ok(videoProgressService.teacherReport(courseId, authentication.getName(), page, size), request.getHeader("X-Request-Id"));
    }
}
