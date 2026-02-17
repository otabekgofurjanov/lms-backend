package com.company.lms.zoom.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record StudentCourseAttendanceResponse(
        UUID courseId,
        BigDecimal attendancePct,
        List<StudentMeetingAttendanceDto> meetings
) {
}
