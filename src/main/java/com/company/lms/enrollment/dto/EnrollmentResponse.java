package com.company.lms.enrollment.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EnrollmentResponse(
        UUID enrollmentId,
        UUID courseId,
        EnrollmentStudentDto student,
        String enrollmentStatus,
        OffsetDateTime enrolledAt
) {
}
