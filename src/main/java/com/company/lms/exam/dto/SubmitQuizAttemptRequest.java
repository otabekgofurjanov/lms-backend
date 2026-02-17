package com.company.lms.exam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record SubmitQuizAttemptRequest(
        @NotEmpty List<@Valid AttemptAnswerRequest> answers
) {
}
