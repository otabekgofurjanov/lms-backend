package com.company.lms.courses.modules.controller;

import com.company.lms.common.dto.ApiResponse;
import com.company.lms.courses.modules.dto.*;
import com.company.lms.courses.modules.service.CourseModuleService;
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
public class ModuleController {
    private final CourseModuleService moduleService;

    @PostMapping("/api/courses/{courseId}/modules")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ApiResponse<ModuleResponse> create(@PathVariable UUID courseId,
                                              @Valid @RequestBody CreateModuleRequest request,
                                              Authentication authentication,
                                              HttpServletRequest httpRequest) {
        return ApiResponse.ok(moduleService.create(courseId, request, authentication.getName(), httpRequest), httpRequest.getHeader("X-Request-Id"));
    }

    @PutMapping("/api/modules/{moduleId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ApiResponse<ModuleResponse> update(@PathVariable UUID moduleId,
                                              @Valid @RequestBody UpdateModuleRequest request,
                                              Authentication authentication,
                                              HttpServletRequest httpRequest) {
        return ApiResponse.ok(moduleService.update(moduleId, request, authentication.getName(), httpRequest), httpRequest.getHeader("X-Request-Id"));
    }

    @DeleteMapping("/api/modules/{moduleId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ApiResponse<String> delete(@PathVariable UUID moduleId, Authentication authentication, HttpServletRequest httpRequest) {
        moduleService.delete(moduleId, authentication.getName(), httpRequest);
        return ApiResponse.ok("deleted", httpRequest.getHeader("X-Request-Id"));
    }

    @PostMapping("/api/courses/{courseId}/modules/reorder")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ApiResponse<List<ModuleResponse>> reorder(@PathVariable UUID courseId,
                                                     @Valid @RequestBody ReorderModulesRequest request,
                                                     Authentication authentication,
                                                     HttpServletRequest httpRequest) {
        return ApiResponse.ok(moduleService.reorder(courseId, request, authentication.getName(), httpRequest), httpRequest.getHeader("X-Request-Id"));
    }

    @GetMapping("/api/courses/{courseId}/modules")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ApiResponse<List<ModuleResponse>> list(@PathVariable UUID courseId, Authentication authentication, HttpServletRequest httpRequest) {
        return ApiResponse.ok(moduleService.listByCourse(courseId, authentication.getName()), httpRequest.getHeader("X-Request-Id"));
    }
}
