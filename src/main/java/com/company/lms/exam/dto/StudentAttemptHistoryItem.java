package com.company.lms.exam.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record StudentAttemptHistoryItem(
        Integer attemptNo,
        BigDecimal scorePct,
        boolean passed,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        String status
) {
}
