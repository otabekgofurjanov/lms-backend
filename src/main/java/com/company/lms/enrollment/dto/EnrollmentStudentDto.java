package com.company.lms.enrollment.dto;

import java.util.UUID;

public record EnrollmentStudentDto(UUID id, String fullName, String email, String phone, String status) {
}
