package com.company.lms.video.dto;

import java.math.BigDecimal;

public record VideoProgressUpdateResponse(
        boolean accepted,
        BigDecimal completionPct,
        boolean canUnlockQuiz
) {
}
