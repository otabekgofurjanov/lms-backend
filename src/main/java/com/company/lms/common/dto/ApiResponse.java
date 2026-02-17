package com.company.lms.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        ApiError error,
        OffsetDateTime timestamp,
        String requestId
) {
    public static <T> ApiResponse<T> ok(T data, String requestId) {
        return ApiResponse.<T>builder().success(true).data(data).timestamp(OffsetDateTime.now()).requestId(requestId).build();
    }

    public static <T> ApiResponse<T> fail(ApiError error, String requestId) {
        return ApiResponse.<T>builder().success(false).error(error).timestamp(OffsetDateTime.now()).requestId(requestId).build();
    }
}
