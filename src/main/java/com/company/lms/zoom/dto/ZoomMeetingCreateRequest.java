package com.company.lms.zoom.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record ZoomMeetingCreateRequest(
        @NotNull OffsetDateTime startTime,
        @NotNull @Min(1) Integer durationMinutes,
        @Size(max = 255) String topic
) {
}
