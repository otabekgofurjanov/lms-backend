package com.company.lms.courses.lessons.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record UpdateLessonRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Pattern(regexp = "LIVE_ZOOM|RECORDED") String lessonType,
        OffsetDateTime availableAt
) {
}
