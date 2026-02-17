package com.company.lms.courses.controller;

import com.company.lms.common.dto.ApiResponse;
import com.company.lms.common.dto.PageResponse;
import com.company.lms.courses.dto.*;
import com.company.lms.courses.service.CourseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {
    private final CourseService courseService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ApiResponse<CourseResponse> create(@Valid @RequestBody CreateCourseRequest request, Authentication authentication, HttpServletRequest httpRequest) {
        return ApiResponse.ok(courseService.create(request, authentication.getName(), httpRequest), httpRequest.getHeader("X-Request-Id"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ApiResponse<CourseResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateCourseRequest request, Authentication authentication, HttpServletRequest httpRequest) {
        return ApiResponse.ok(courseService.update(id, request, authentication.getName(), httpRequest), httpRequest.getHeader("X-Request-Id"));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ApiResponse<CourseResponse> updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateCourseStatusRequest request, Authentication authentication, HttpServletRequest httpRequest) {
        return ApiResponse.ok(courseService.updateStatus(id, request, authentication.getName(), httpRequest), httpRequest.getHeader("X-Request-Id"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ApiResponse<PageResponse<CourseResponse>> list(@RequestParam(required = false) String search,
                                                          @RequestParam(required = false) String status,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size,
                                                          HttpServletRequest httpRequest) {
        return ApiResponse.ok(courseService.list(search, status, page, size), httpRequest.getHeader("X-Request-Id"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ApiResponse<CourseResponse> get(@PathVariable UUID id, HttpServletRequest httpRequest) {
        return ApiResponse.ok(courseService.get(id), httpRequest.getHeader("X-Request-Id"));
    }
}
