package com.company.lms.zoom.controller;

import com.company.lms.common.dto.ApiResponse;
import com.company.lms.zoom.dto.StudentJoinLinkResponse;
import com.company.lms.zoom.dto.ZoomMeetingCreateRequest;
import com.company.lms.zoom.dto.ZoomMeetingResponse;
import com.company.lms.zoom.service.ZoomService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ZoomMeetingController {
    private final ZoomService zoomService;

    @PostMapping("/api/lessons/{lessonId}/zoom-meeting")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<ZoomMeetingResponse> create(@PathVariable UUID lessonId,
                                                   @Valid @RequestBody ZoomMeetingCreateRequest request,
                                                   Authentication authentication,
                                                   HttpServletRequest httpRequest) {
        return ApiResponse.ok(zoomService.createMeeting(lessonId, request, authentication.getName(), httpRequest), httpRequest.getHeader("X-Request-Id"));
    }

    @GetMapping("/api/lessons/{lessonId}/zoom-meeting")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<ZoomMeetingResponse> get(@PathVariable UUID lessonId,
                                                Authentication authentication,
                                                HttpServletRequest request) {
        return ApiResponse.ok(zoomService.getMeeting(lessonId, authentication.getName()), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/api/student/lessons/{lessonId}/join-link")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<StudentJoinLinkResponse> joinLink(@PathVariable UUID lessonId,
                                                         Authentication authentication,
                                                         HttpServletRequest request) {
        return ApiResponse.ok(zoomService.studentJoinLink(lessonId, authentication.getName()), request.getHeader("X-Request-Id"));
    }
}
