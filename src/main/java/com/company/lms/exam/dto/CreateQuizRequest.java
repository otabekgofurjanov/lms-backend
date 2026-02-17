package com.company.lms.exam.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateQuizRequest(
        @NotNull UUID courseId,
        UUID lessonId,
        @NotBlank String title,
        Integer timeLimitSec,
        @NotNull @Min(1) Integer maxAttempts,
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal passScorePct,
        @NotNull Boolean isActive
) {
}
