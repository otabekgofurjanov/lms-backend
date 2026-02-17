package com.company.lms.courses.lessons.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record ReorderLessonsRequest(@NotEmpty List<UUID> lessonIdsInOrder) {
}
