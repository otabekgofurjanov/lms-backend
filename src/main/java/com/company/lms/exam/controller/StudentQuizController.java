package com.company.lms.exam.controller;

import com.company.lms.common.dto.ApiResponse;
import com.company.lms.exam.dto.*;
import com.company.lms.exam.service.QuizAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentQuizController {
    private final QuizAttemptService quizAttemptService;

    @GetMapping("/courses/{courseId}/quizzes")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<List<StudentQuizListItem>> list(@PathVariable UUID courseId, Authentication auth, HttpServletRequest request) {
        return ApiResponse.ok(quizAttemptService.studentList(courseId, auth.getName()), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/quizzes/{quizId}/start")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<StudentQuizAttemptResponse> start(@PathVariable UUID quizId, Authentication auth, HttpServletRequest request) {
        return ApiResponse.ok(quizAttemptService.start(quizId, auth.getName(), request), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/attempts/{attemptId}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<StudentQuizResultResponse> submit(@PathVariable UUID attemptId,
                                                         @Valid @RequestBody SubmitQuizAttemptRequest body,
                                                         Authentication auth,
                                                         HttpServletRequest request) {
        return ApiResponse.ok(quizAttemptService.submit(attemptId, body, auth.getName(), request), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/quizzes/{quizId}/attempts")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<List<StudentAttemptHistoryItem>> history(@PathVariable UUID quizId, Authentication auth, HttpServletRequest request) {
        return ApiResponse.ok(quizAttemptService.history(quizId, auth.getName()), request.getHeader("X-Request-Id"));
    }
}
