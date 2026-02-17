package com.company.lms.courses.modules.dto;

import java.util.UUID;

public record ModuleResponse(UUID id, UUID courseId, String title, int sortOrder) {
}
