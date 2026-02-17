package com.company.lms.exam.controller;

import com.company.lms.common.dto.ApiResponse;
import com.company.lms.common.dto.PageResponse;
import com.company.lms.exam.dto.*;
import com.company.lms.exam.service.QuizService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/exam/quizzes")
@RequiredArgsConstructor
public class QuizController {
    private final QuizService quizService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ApiResponse<QuizAdminResponse> create(@Valid @RequestBody CreateQuizRequest req, Authentication auth, HttpServletRequest request) {
        return ApiResponse.ok(quizService.create(req, auth.getName(), request), request.getHeader("X-Request-Id"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ApiResponse<QuizAdminResponse> update(@PathVariable UUID id, @Valid @RequestBody CreateQuizRequest req, Authentication auth, HttpServletRequest request) {
        return ApiResponse.ok(quizService.update(id, req, auth.getName(), request), request.getHeader("X-Request-Id"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ApiResponse<String> delete(@PathVariable UUID id, Authentication auth, HttpServletRequest request) {
        return ApiResponse.ok(quizService.delete(id, auth.getName(), request), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/{quizId}/questions")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ApiResponse<String> attach(@PathVariable UUID quizId, @Valid @RequestBody AttachQuestionsRequest req, Authentication auth, HttpServletRequest request) {
        return ApiResponse.ok(quizService.attachQuestions(quizId, req, auth.getName(), request), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/{quizId}/questions/reorder")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ApiResponse<String> reorder(@PathVariable UUID quizId, @Valid @RequestBody ReorderQuizQuestionsRequest req, Authentication auth, HttpServletRequest request) {
        return ApiResponse.ok(quizService.reorderQuestions(quizId, req, auth.getName(), request), request.getHeader("X-Request-Id"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ApiResponse<PageResponse<QuizAdminResponse>> list(@RequestParam UUID courseId,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size,
                                                              Authentication auth,
                                                              HttpServletRequest request) {
        return ApiResponse.ok(quizService.list(auth.getName(), courseId, page, size), request.getHeader("X-Request-Id"));
    }
}
