package com.company.lms.courses.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateCourseStatusRequest(
        @NotBlank @Pattern(regexp = "DRAFT|ACTIVE|ARCHIVED") String status
) {
}
