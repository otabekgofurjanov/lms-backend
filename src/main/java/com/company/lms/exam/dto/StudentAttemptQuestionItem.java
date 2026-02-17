package com.company.lms.exam.dto;

import java.util.List;
import java.util.UUID;

public record StudentAttemptQuestionItem(
        UUID questionId,
        String text,
        List<String> options
) {
}
