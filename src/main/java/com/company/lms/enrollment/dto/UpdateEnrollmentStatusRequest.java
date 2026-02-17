package com.company.lms.enrollment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateEnrollmentStatusRequest(
        @NotBlank @Pattern(regexp = "ACTIVE|PAUSED|REMOVED") String status
) {
}
