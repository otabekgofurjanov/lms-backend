package com.company.lms.exam.controller;

import com.company.lms.common.dto.ApiResponse;
import com.company.lms.common.dto.PageResponse;
import com.company.lms.exam.dto.CreateQuestionRequest;
import com.company.lms.exam.dto.QuestionAdminResponse;
import com.company.lms.exam.service.QuestionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/exam/questions")
@RequiredArgsConstructor
public class QuestionController {
    private final QuestionService questionService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ApiResponse<QuestionAdminResponse> create(@Valid @RequestBody CreateQuestionRequest req, Authentication auth, HttpServletRequest request) {
        return ApiResponse.ok(questionService.create(req, auth.getName(), request), request.getHeader("X-Request-Id"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ApiResponse<QuestionAdminResponse> update(@PathVariable UUID id, @Valid @RequestBody CreateQuestionRequest req, Authentication auth, HttpServletRequest request) {
        return ApiResponse.ok(questionService.update(id, req, auth.getName(), request), request.getHeader("X-Request-Id"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ApiResponse<String> delete(@PathVariable UUID id, Authentication auth, HttpServletRequest request) {
        return ApiResponse.ok(questionService.delete(id, auth.getName(), request), request.getHeader("X-Request-Id"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ApiResponse<PageResponse<QuestionAdminResponse>> list(@RequestParam(required = false) String search,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size,
                                                                 Authentication auth,
                                                                 HttpServletRequest request) {
        return ApiResponse.ok(questionService.list(auth.getName(), search, page, size), request.getHeader("X-Request-Id"));
    }
}
