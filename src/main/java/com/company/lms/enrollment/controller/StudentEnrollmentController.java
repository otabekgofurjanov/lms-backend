package com.company.lms.enrollment.controller;

import com.company.lms.common.dto.ApiResponse;
import com.company.lms.common.dto.PageResponse;
import com.company.lms.enrollment.dto.StudentCourseDetailResponse;
import com.company.lms.enrollment.dto.StudentCourseResponse;
import com.company.lms.enrollment.service.EnrollmentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/student/courses")
@RequiredArgsConstructor
public class StudentEnrollmentController {
    private final EnrollmentService enrollmentService;

    @GetMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<PageResponse<StudentCourseResponse>> myCourses(@RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "20") int size,
                                                                      Authentication authentication,
                                                                      HttpServletRequest request) {
        return ApiResponse.ok(enrollmentService.listStudentCourses(authentication.getName(), page, size), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/{courseId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<StudentCourseDetailResponse> detail(@PathVariable UUID courseId,
                                                           Authentication authentication,
                                                           HttpServletRequest request) {
        return ApiResponse.ok(enrollmentService.studentCourseDetail(courseId, authentication.getName()), request.getHeader("X-Request-Id"));
    }
}
