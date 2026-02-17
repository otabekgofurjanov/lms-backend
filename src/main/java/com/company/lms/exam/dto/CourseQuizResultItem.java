package com.company.lms.exam.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CourseQuizResultItem(
        UUID studentId,
        String fullName,
        UUID quizId,
        String quizTitle,
        BigDecimal bestScorePct,
        long attemptsCount
) {
}
