package com.company.lms.video.mapper;

import com.company.lms.video.dto.VideoAccessResponse;
import com.company.lms.video.entity.LessonRecordingEntity;
import com.company.lms.video.entity.VideoAssetEntity;
import org.springframework.stereotype.Component;

@Component
public class VideoMapper {
    public VideoAccessResponse toResponse(LessonRecordingEntity recording, VideoAssetEntity asset, String presignedUrl, Long expiresInSeconds) {
        return new VideoAccessResponse(
                recording.getStatus(),
                presignedUrl,
                expiresInSeconds,
                asset != null ? asset.getChecksumSha256() : null,
                asset != null ? asset.getContentType() : null,
                asset != null ? asset.getSizeBytes() : null
        );
    }
}
