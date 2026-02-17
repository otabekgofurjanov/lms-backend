package com.company.lms.zoom.dto;

import com.company.lms.common.dto.PageResponse;
import com.company.lms.completion.dto.StudentAttendanceSummaryDto;

import java.util.UUID;

public record TeacherCourseAttendanceResponse(
        UUID courseId,
        int totalMeetings,
        PageResponse<StudentAttendanceSummaryDto> students
) {
}
