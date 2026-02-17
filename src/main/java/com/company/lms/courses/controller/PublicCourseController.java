package com.company.lms.courses.controller;

import com.company.lms.common.dto.ApiResponse;
import com.company.lms.common.dto.PageResponse;
import com.company.lms.courses.dto.CoursePublicDetailResponse;
import com.company.lms.courses.dto.CoursePublicResponse;
import com.company.lms.courses.service.CourseService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/public/courses")
@RequiredArgsConstructor
public class PublicCourseController {
    private final CourseService courseService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PageResponse<CoursePublicResponse>> list(@RequestParam(required = false) String search,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "20") int size,
                                                                HttpServletRequest request) {
        return ApiResponse.ok(courseService.listActive(search, page, size), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<CoursePublicDetailResponse> detail(@PathVariable UUID id, HttpServletRequest request) {
        return ApiResponse.ok(courseService.getActiveDetail(id), request.getHeader("X-Request-Id"));
    }
}
