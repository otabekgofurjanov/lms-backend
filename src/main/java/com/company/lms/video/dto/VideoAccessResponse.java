package com.company.lms.video.dto;

public record VideoAccessResponse(
        String status,
        String videoUrl,
        Long expiresInSeconds,
        String checksumSha256,
        String contentType,
        Long sizeBytes
) {
}
