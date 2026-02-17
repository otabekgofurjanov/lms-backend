package com.company.lms.exam.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record StudentQuizResultResponse(
        UUID attemptId,
        UUID quizId,
        Integer totalQuestions,
        Integer correctCount,
        BigDecimal scorePct,
        boolean passed,
        OffsetDateTime finishedAt
) {
}
