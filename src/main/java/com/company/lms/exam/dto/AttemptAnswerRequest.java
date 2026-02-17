package com.company.lms.exam.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AttemptAnswerRequest(
        @NotNull UUID questionId,
        @NotNull @Min(0) Integer selectedIndex
) {
}
