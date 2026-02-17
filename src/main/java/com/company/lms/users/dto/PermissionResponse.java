package com.company.lms.users.dto;

import java.util.UUID;

public record PermissionResponse(UUID id, String code, String description) {
}
