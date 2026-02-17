package com.company.lms.enrollment.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StudentCourseResponse(
        UUID courseId,
        String title,
        String coverUrl,
        String status,
        OffsetDateTime enrolledAt
) {
}
