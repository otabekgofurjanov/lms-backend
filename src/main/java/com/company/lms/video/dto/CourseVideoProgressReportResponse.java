package com.company.lms.video.dto;

import com.company.lms.common.dto.PageResponse;

import java.math.BigDecimal;

public record CourseVideoProgressReportResponse(
        BigDecimal overallAvgCompletionPct,
        PageResponse<CourseVideoProgressStudentResponse> students
) {
}
