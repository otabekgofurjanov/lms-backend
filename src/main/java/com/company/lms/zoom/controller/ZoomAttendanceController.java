package com.company.lms.zoom.controller;

import com.company.lms.common.dto.ApiResponse;
import com.company.lms.zoom.dto.*;
import com.company.lms.zoom.service.ZoomAttendanceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ZoomAttendanceController {
    private final ZoomAttendanceService attendanceService;

    @PostMapping("/api/admin/zoom/meetings/{meetingId}/recalculate-attendance")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AttendanceRecalculateResponse> recalcMeeting(@PathVariable UUID meetingId, HttpServletRequest request) {
        return ApiResponse.ok(attendanceService.recalculateMeeting(meetingId), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/api/admin/courses/{courseId}/recalculate-attendance")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CourseAttendanceRecalculateResponse> recalcCourse(@PathVariable UUID courseId, HttpServletRequest request) {
        return ApiResponse.ok(attendanceService.recalculateCourse(courseId), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/api/zoom/participants/{participantId}/manual-match")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public ApiResponse<String> manualMatch(@PathVariable UUID participantId,
                                           @Valid @RequestBody ManualParticipantMatchRequest req,
                                           Authentication authentication,
                                           HttpServletRequest request) {
        attendanceService.manualMatch(participantId, req.studentId(), authentication.getName());
        return ApiResponse.ok("matched", request.getHeader("X-Request-Id"));
    }

    @GetMapping("/api/teacher/courses/{courseId}/attendance")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<TeacherCourseAttendanceResponse> teacherReport(@PathVariable UUID courseId,
                                                                      @RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "20") int size,
                                                                      Authentication authentication,
                                                                      HttpServletRequest request) {
        return ApiResponse.ok(attendanceService.teacherReport(courseId, authentication.getName(), page, size), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/api/student/courses/{courseId}/attendance")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<StudentCourseAttendanceResponse> studentReport(@PathVariable UUID courseId,
                                                                      Authentication authentication,
                                                                      HttpServletRequest request) {
        return ApiResponse.ok(attendanceService.studentReport(courseId, authentication.getName()), request.getHeader("X-Request-Id"));
    }
}
