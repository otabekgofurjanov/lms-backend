package com.company.lms.courses.dto;

import java.util.UUID;

public record CoursePublicResponse(
        UUID id,
        String title,
        String coverUrl,
        String shortDescription
) {
}
