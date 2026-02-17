package com.company.lms.exam.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record StudentQuizListItem(
        UUID quizId,
        String title,
        UUID lessonId,
        BigDecimal passScorePct,
        Integer maxAttempts,
        long attemptsUsed,
        boolean canStart
) {
}
