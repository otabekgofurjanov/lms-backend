package com.company.lms.zoom.dto;

import java.util.UUID;

public record AttendanceRecalculateResponse(
        UUID meetingId,
        int processedParticipants,
        int matched,
        int unmatched,
        int updatedStatuses
) {
}
