package com.company.lms.courses.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCourseRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 5000) String description,
        @Size(max = 3000) String coverUrl
) {
}
