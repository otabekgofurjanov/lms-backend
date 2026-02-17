package com.company.lms.exam.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record QuestionAdminResponse(
        UUID id,
        String text,
        List<String> options,
        Integer correctIndex,
        String explanation,
        OffsetDateTime createdAt
) {
}
