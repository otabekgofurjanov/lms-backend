package com.company.lms.courses.modules.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record ReorderModulesRequest(@NotEmpty List<UUID> moduleIdsInOrder) {
}
