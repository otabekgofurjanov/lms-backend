package com.company.lms.courses.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CourseResponse(
        UUID id,
        String title,
        String description,
        String coverUrl,
        String status,
        UUID createdBy,
        OffsetDateTime createdAt
) {
}
