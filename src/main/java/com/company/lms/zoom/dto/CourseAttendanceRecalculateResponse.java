package com.company.lms.zoom.dto;

import java.util.UUID;

public record CourseAttendanceRecalculateResponse(
        UUID courseId,
        int meetingsProcessed,
        int participantsProcessed,
        int matched,
        int unmatched,
        int statusesUpdated
) {
}
