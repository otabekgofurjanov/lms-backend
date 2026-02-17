package com.company.lms.common.dto;

import java.util.Map;

public record ApiError(String code, String message, Map<String, String> details) {
}
