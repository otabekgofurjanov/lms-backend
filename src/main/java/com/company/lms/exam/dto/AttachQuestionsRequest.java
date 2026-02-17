package com.company.lms.exam.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record AttachQuestionsRequest(
        @NotEmpty List<UUID> questionIds
) {
}
