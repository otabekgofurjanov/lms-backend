package com.company.lms.video.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record VideoSessionResponse(
        UUID sessionId,
        UUID lessonId,
        OffsetDateTime serverTime,
        int requiredCompletionPct
) {
}
