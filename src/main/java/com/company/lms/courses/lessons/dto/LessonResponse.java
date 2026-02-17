package com.company.lms.courses.lessons.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LessonResponse(
        UUID id,
        UUID moduleId,
        String title,
        String lessonType,
        int sortOrder,
        OffsetDateTime availableAt
) {
}
