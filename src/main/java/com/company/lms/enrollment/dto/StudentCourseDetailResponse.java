package com.company.lms.enrollment.dto;

import com.company.lms.courses.lessons.dto.LessonResponse;
import com.company.lms.courses.modules.dto.ModuleResponse;

import java.util.List;
import java.util.UUID;

public record StudentCourseDetailResponse(
        UUID courseId,
        String title,
        String description,
        String coverUrl,
        List<ModuleWithLessons> modules
) {
    public record ModuleWithLessons(ModuleResponse module, List<LessonResponse> lessons) {}
}
