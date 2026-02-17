package com.company.lms.zoom.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ZoomMeetingResponse(
        UUID id,
        UUID lessonId,
        String zoomMeetingId,
        String joinUrl,
        OffsetDateTime startTime,
        Integer durationMinutes,
        String status
) {
}
