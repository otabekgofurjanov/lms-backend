package com.company.lms.exam.controller;

import com.company.lms.common.dto.ApiResponse;
import com.company.lms.common.dto.PageResponse;
import com.company.lms.exam.dto.CourseQuizResultItem;
import com.company.lms.exam.service.QuizAttemptService;
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
@RequiredArgsConstructor
public class QuizResultsController {
    private final QuizAttemptService quizAttemptService;

    @GetMapping("/api/teacher/courses/{courseId}/quiz-results")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<PageResponse<CourseQuizResultItem>> teacher(@PathVariable UUID courseId,
                                                                   @RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "20") int size,
                                                                   Authentication auth,
                                                                   HttpServletRequest request) {
        return ApiResponse.ok(quizAttemptService.teacherResults(courseId, auth.getName(), page, size), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/api/admin/courses/{courseId}/quiz-results")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<CourseQuizResultItem>> admin(@PathVariable UUID courseId,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size,
                                                                 Authentication auth,
                                                                 HttpServletRequest request) {
        return ApiResponse.ok(quizAttemptService.adminResults(courseId, auth.getName(), page, size), request.getHeader("X-Request-Id"));
    }
}
