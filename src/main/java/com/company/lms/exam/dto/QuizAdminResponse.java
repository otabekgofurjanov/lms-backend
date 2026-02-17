package com.company.lms.exam.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record QuizAdminResponse(
        UUID id,
        UUID courseId,
        UUID lessonId,
        String title,
        Integer timeLimitSec,
        Integer maxAttempts,
        BigDecimal passScorePct,
        Boolean isActive,
        OffsetDateTime createdAt
) {
}
