package com.company.lms.completion.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record StudentAttendanceSummaryDto(
        UUID studentId,
        String fullName,
        BigDecimal attendancePct,
        String lastMeetingStatus
) {
}
