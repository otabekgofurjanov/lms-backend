package com.company.lms.users.dto;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String fullName,
        String email,
        String phone,
        String status,
        Set<String> roles,
        OffsetDateTime createdAt
) {
}
