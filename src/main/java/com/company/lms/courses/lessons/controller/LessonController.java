package com.company.lms.courses.lessons.controller;

import com.company.lms.common.dto.ApiResponse;
import com.company.lms.courses.lessons.dto.*;
import com.company.lms.courses.lessons.service.LessonService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class LessonController {
    private final LessonService lessonService;

    @PostMapping("/api/modules/{moduleId}/lessons")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ApiResponse<LessonResponse> create(@PathVariable UUID moduleId,
                                              @Valid @RequestBody CreateLessonRequest request,
                                              Authentication authentication,
                                              HttpServletRequest httpRequest) {
        return ApiResponse.ok(lessonService.create(moduleId, request, authentication.getName(), httpRequest), httpRequest.getHeader("X-Request-Id"));
    }

    @PutMapping("/api/lessons/{lessonId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ApiResponse<LessonResponse> update(@PathVariable UUID lessonId,
                                              @Valid @RequestBody UpdateLessonRequest request,
                                              Authentication authentication,
                                              HttpServletRequest httpRequest) {
        return ApiResponse.ok(lessonService.update(lessonId, request, authentication.getName(), httpRequest), httpRequest.getHeader("X-Request-Id"));
    }

    @DeleteMapping("/api/lessons/{lessonId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ApiResponse<String> delete(@PathVariable UUID lessonId, Authentication authentication, HttpServletRequest httpRequest) {
        lessonService.delete(lessonId, authentication.getName(), httpRequest);
        return ApiResponse.ok("deleted", httpRequest.getHeader("X-Request-Id"));
    }

    @PostMapping("/api/modules/{moduleId}/lessons/reorder")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ApiResponse<List<LessonResponse>> reorder(@PathVariable UUID moduleId,
                                                     @Valid @RequestBody ReorderLessonsRequest request,
                                                     Authentication authentication,
                                                     HttpServletRequest httpRequest) {
        return ApiResponse.ok(lessonService.reorder(moduleId, request, authentication.getName(), httpRequest), httpRequest.getHeader("X-Request-Id"));
    }

    @GetMapping("/api/modules/{moduleId}/lessons")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ApiResponse<List<LessonResponse>> list(@PathVariable UUID moduleId, Authentication authentication, HttpServletRequest httpRequest) {
        return ApiResponse.ok(lessonService.listByModule(moduleId, authentication.getName()), httpRequest.getHeader("X-Request-Id"));
    }
}
