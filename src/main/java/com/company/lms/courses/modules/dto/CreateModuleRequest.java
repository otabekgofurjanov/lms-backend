package com.company.lms.courses.modules.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateModuleRequest(@NotBlank @Size(max = 255) String title) {
}
