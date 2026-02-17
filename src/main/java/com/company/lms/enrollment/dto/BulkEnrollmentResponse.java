package com.company.lms.enrollment.dto;

import java.util.List;

public record BulkEnrollmentResponse(
        int total,
        int created,
        int reactivated,
        int skipped,
        int failed,
        List<String> errors
) {
}
