package com.company.lms.exam.dto;

import java.util.List;
import java.util.UUID;

public record StudentQuizAttemptResponse(
        UUID attemptId,
        UUID quizId,
        Integer timeLimitSec,
        List<StudentAttemptQuestionItem> questions
) {
}
