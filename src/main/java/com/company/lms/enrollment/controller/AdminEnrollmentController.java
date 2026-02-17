package com.company.lms.enrollment.controller;

import com.company.lms.common.dto.ApiResponse;
import com.company.lms.common.dto.PageResponse;
import com.company.lms.enrollment.dto.*;
import com.company.lms.enrollment.service.EnrollmentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminEnrollmentController {
    private final EnrollmentService enrollmentService;

    @PostMapping("/courses/{courseId}/enrollments")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<BulkEnrollmentResponse> bulkEnroll(@PathVariable UUID courseId,
                                                          @Valid @RequestBody BulkEnrollmentRequest request,
                                                          Authentication authentication,
                                                          HttpServletRequest httpRequest) {
        return ApiResponse.ok(enrollmentService.bulkEnroll(courseId, request, authentication.getName(), httpRequest), httpRequest.getHeader("X-Request-Id"));
    }

    @DeleteMapping("/enrollments/{enrollmentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<EnrollmentResponse> remove(@PathVariable UUID enrollmentId,
                                                  Authentication authentication,
                                                  HttpServletRequest httpRequest) {
        return ApiResponse.ok(enrollmentService.remove(enrollmentId, authentication.getName(), httpRequest), httpRequest.getHeader("X-Request-Id"));
    }

    @PatchMapping("/enrollments/{enrollmentId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<EnrollmentResponse> updateStatus(@PathVariable UUID enrollmentId,
                                                        @Valid @RequestBody UpdateEnrollmentStatusRequest request,
                                                        Authentication authentication,
                                                        HttpServletRequest httpRequest) {
        return ApiResponse.ok(enrollmentService.updateStatus(enrollmentId, request, authentication.getName(), httpRequest), httpRequest.getHeader("X-Request-Id"));
    }

    @GetMapping("/courses/{courseId}/enrollments")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<EnrollmentResponse>> list(@PathVariable UUID courseId,
                                                              @RequestParam(required = false) String search,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size,
                                                              HttpServletRequest request) {
        return ApiResponse.ok(enrollmentService.listCourseEnrollmentsForAdmin(courseId, search, page, size), request.getHeader("X-Request-Id"));
    }
}
