package com.company.lms.video.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record CourseVideoProgressStudentResponse(
        UUID studentId,
        String fullName,
        BigDecimal avgCompletionPct,
        OffsetDateTime lastEventAt,
        Map<String, Object> suspiciousSummary
) {
}
