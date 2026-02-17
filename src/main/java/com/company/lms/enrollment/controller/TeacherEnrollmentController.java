package com.company.lms.enrollment.controller;

import com.company.lms.common.dto.ApiResponse;
import com.company.lms.common.dto.PageResponse;
import com.company.lms.enrollment.dto.EnrollmentResponse;
import com.company.lms.enrollment.service.EnrollmentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/teacher")
@RequiredArgsConstructor
public class TeacherEnrollmentController {
    private final EnrollmentService enrollmentService;

    @GetMapping("/courses/{courseId}/students")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<PageResponse<EnrollmentResponse>> students(@PathVariable UUID courseId,
                                                                  @RequestParam(required = false) String search,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "20") int size,
                                                                  Authentication authentication,
                                                                  HttpServletRequest request) {
        return ApiResponse.ok(enrollmentService.listCourseEnrollmentsForTeacher(courseId, authentication.getName(), search, page, size), request.getHeader("X-Request-Id"));
    }
}
