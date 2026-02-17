package com.company.lms.video.service;

import com.company.lms.courses.service.CourseAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VideoAdminService {
    private final CourseAccessService courseAccessService;
    private final RecordingIngestService recordingIngestService;

    @Transactional
    public String retry(UUID lessonId, String adminEmail) {
        var admin = courseAccessService.requireActor(adminEmail);
        recordingIngestService.retryFailedRecording(lessonId, admin.getId());
        return "retry_started";
    }
}
