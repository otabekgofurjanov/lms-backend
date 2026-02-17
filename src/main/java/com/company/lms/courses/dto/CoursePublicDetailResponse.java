package com.company.lms.courses.dto;

import java.util.UUID;

public record CoursePublicDetailResponse(
        UUID id,
        String title,
        String description,
        String coverUrl,
        String status
) {
}
