package com.company.lms.zoom.dto;

import java.util.UUID;

public record StudentMeetingAttendanceDto(
        UUID meetingId,
        String lessonTitle,
        String status,
        int durationSeconds
) {
}
