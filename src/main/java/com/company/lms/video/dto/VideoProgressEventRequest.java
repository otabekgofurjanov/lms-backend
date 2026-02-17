package com.company.lms.video.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

public record VideoProgressEventRequest(
        @NotNull UUID sessionId,
        @Min(0) int currentSecond,
        @Min(0) int watchedDeltaSeconds,
        @Min(1) int totalSeconds,
        @NotNull OffsetDateTime eventTime,
        @NotNull Boolean tabVisible,
        @Min(0) int tabSwitchCountDelta,
        @Min(0) int seekAttemptDelta
) {
}
