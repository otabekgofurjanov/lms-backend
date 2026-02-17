package com.company.lms.users.dto;

import java.util.List;

public record UserImportResultResponse(
        int total,
        int created,
        int updated,
        int failed,
        List<String> errors
) {
}
