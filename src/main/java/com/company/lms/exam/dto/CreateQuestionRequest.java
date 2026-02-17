package com.company.lms.exam.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateQuestionRequest(
        @NotBlank String text,
        @NotEmpty List<@NotBlank String> options,
        @NotNull @Min(0) Integer correctIndex,
        String explanation
) {
}
