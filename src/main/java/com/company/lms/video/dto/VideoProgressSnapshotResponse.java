package com.company.lms.video.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

public record VideoProgressSnapshotResponse(
        int watchedSeconds,
        int totalSeconds,
        BigDecimal completionPct,
        Map<String, Object> suspiciousFlags,
        OffsetDateTime lastEventAt
) {
}
