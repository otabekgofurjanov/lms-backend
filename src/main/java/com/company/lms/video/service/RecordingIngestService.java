package com.company.lms.video.service;

import com.company.lms.audit.service.AuditService;
import com.company.lms.common.exception.AppException;
import com.company.lms.courses.lessons.entity.LessonEntity;
import com.company.lms.courses.lessons.repository.LessonRepository;
import com.company.lms.courses.modules.entity.CourseModuleEntity;
import com.company.lms.courses.modules.repository.CourseModuleRepository;
import com.company.lms.video.entity.LessonRecordingEntity;
import com.company.lms.video.entity.VideoAssetEntity;
import com.company.lms.video.repository.LessonRecordingRepository;
import com.company.lms.video.repository.VideoAssetRepository;
import com.company.lms.zoom.entity.ZoomAccountEntity;
import com.company.lms.zoom.entity.ZoomMeetingEntity;
import com.company.lms.zoom.repository.ZoomAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Iterator;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecordingIngestService {
    private final LessonRecordingRepository lessonRecordingRepository;
    private final VideoAssetRepository videoAssetRepository;
    private final LessonRepository lessonRepository;
    private final CourseModuleRepository moduleRepository;
    private final ZoomAccountRepository zoomAccountRepository;
    private final MinioStorageService minioStorageService;
    private final MinioProperties minioProperties;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    @Async
    @Transactional
    public void handleZoomRecordingCompleted(ZoomMeetingEntity meeting, String rawPayload) {
        LessonRecordingEntity recording = lessonRecordingRepository.findByLessonId(meeting.getLessonId()).orElseGet(() -> newRecording(meeting.getLessonId()));
        if ("READY".equals(recording.getStatus())) {
            return;
        }

        recording.setStatus("PROCESSING");
        recording.setErrorMessage(null);
        lessonRecordingRepository.save(recording);
        auditService.log(meeting.getCreatedBy(), "RECORDING_INGEST_STARTED", "LESSON", meeting.getLessonId().toString(), null, "{\"retry\":false}", null, null);

        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            JsonNode object = root.path("payload").path("object");
            String zoomRecordingId = object.path("uuid").asText(object.path("id").asText(null));
            RecordingFile recordingFile = selectMp4(object.path("recording_files"));
            if (recordingFile == null || recordingFile.downloadUrl() == null) {
                throw new AppException(HttpStatus.BAD_REQUEST, "ZOOM_RECORDING_NOT_FOUND", "MP4 recording file not found in webhook payload");
            }

            ZoomAccountEntity account = zoomAccountRepository.findById(meeting.getZoomAccountId())
                    .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "ZOOM_ACCOUNT_NOT_FOUND", "Zoom account not found for meeting"));
            if (account.getAccessToken() == null) {
                throw new AppException(HttpStatus.BAD_REQUEST, "ZOOM_TOKEN_MISSING", "Zoom access token is missing");
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(recordingFile.downloadUrl()))
                    .header("Authorization", "Bearer " + account.getAccessToken())
                    .GET()
                    .build();
            HttpResponse<byte[]> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                throw new AppException(HttpStatus.BAD_GATEWAY, "ZOOM_DOWNLOAD_FAILED", "Could not download recording from Zoom");
            }

            byte[] bytes = response.body();
            String checksum = sha256(bytes);

            LessonEntity lesson = lessonRepository.findById(meeting.getLessonId())
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "LESSON_NOT_FOUND", "Lesson not found"));
            CourseModuleEntity module = moduleRepository.findById(lesson.getModuleId())
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "MODULE_NOT_FOUND", "Module not found"));

            String bucket = minioProperties.bucket();
            minioStorageService.ensureBucketExists(bucket);
            String objectKey = buildObjectKey(module.getCourseId(), lesson.getId(), recording.getId(), recordingFile.fileName());
            minioStorageService.upload(bucket, objectKey, new ByteArrayInputStream(bytes), bytes.length, recordingFile.contentType());

            VideoAssetEntity asset = videoAssetRepository.findTopByRecordingIdOrderByCreatedAtDesc(recording.getId()).orElseGet(VideoAssetEntity::new);
            if (asset.getId() == null) {
                asset.setId(UUID.randomUUID());
                asset.setRecordingId(recording.getId());
                asset.setCreatedAt(OffsetDateTime.now());
            }
            asset.setStorageProvider("MINIO");
            asset.setBucket(bucket);
            asset.setObjectKey(objectKey);
            asset.setContentType(recordingFile.contentType());
            asset.setSizeBytes((long) bytes.length);
            asset.setChecksumSha256(checksum);
            videoAssetRepository.save(asset);

            recording.setZoomRecordingId(zoomRecordingId);
            recording.setStatus("READY");
            recording.setRecordedAt(OffsetDateTime.now());
            lessonRecordingRepository.save(recording);
            auditService.log(meeting.getCreatedBy(), "RECORDING_INGEST_COMPLETED", "LESSON", meeting.getLessonId().toString(), null, "{\"recordingId\":\"" + recording.getId() + "\"}", null, null);
        } catch (Exception ex) {
            recording.setStatus("FAILED");
            recording.setErrorMessage(ex.getMessage());
            lessonRecordingRepository.save(recording);
            auditService.log(meeting.getCreatedBy(), "RECORDING_INGEST_FAILED", "LESSON", meeting.getLessonId().toString(), null, "{\"error\":\"" + safe(ex.getMessage()) + "\"}", null, null);
        }
    }

    @Transactional
    public void retryFailedRecording(UUID lessonId, UUID actorId) {
        LessonRecordingEntity recording = lessonRecordingRepository.findByLessonId(lessonId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "RECORDING_NOT_FOUND", "Lesson recording not found"));
        if (!"FAILED".equals(recording.getStatus())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "RETRY_NOT_ALLOWED", "Retry is only allowed when recording is FAILED");
        }
        recording.setStatus("PENDING");
        recording.setErrorMessage(null);
        lessonRecordingRepository.save(recording);
        auditService.log(actorId, "RECORDING_INGEST_STARTED", "LESSON", lessonId.toString(), null, "{\"retry\":true}", null, null);
    }

    private LessonRecordingEntity newRecording(UUID lessonId) {
        LessonRecordingEntity recording = new LessonRecordingEntity();
        recording.setId(UUID.randomUUID());
        recording.setLessonId(lessonId);
        recording.setStatus("PENDING");
        recording.setCreatedAt(OffsetDateTime.now());
        return recording;
    }

    private RecordingFile selectMp4(JsonNode filesNode) {
        if (filesNode == null || !filesNode.isArray()) {
            return null;
        }
        Iterator<JsonNode> it = filesNode.iterator();
        while (it.hasNext()) {
            JsonNode file = it.next();
            if (!"MP4".equalsIgnoreCase(file.path("file_type").asText(""))) {
                continue;
            }
            String downloadUrl = file.path("download_url").asText(null);
            String contentType = file.path("file_extension").asText(null);
            String fileName = file.path("recording_type").asText("recording") + ".mp4";
            return new RecordingFile(downloadUrl, (contentType == null || contentType.isBlank()) ? "video/mp4" : contentType, fileName);
        }
        return null;
    }

    private String buildObjectKey(UUID courseId, UUID lessonId, UUID recordingId, String fileName) {
        return "courses/" + courseId + "/lessons/" + lessonId + "/recordings/" + recordingId + "/" + fileName;
    }

    private String sha256(byte[] content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(content);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String safe(String text) {
        if (text == null) {
            return "unknown";
        }
        return text.replace("\"", "'");
    }

    private record RecordingFile(String downloadUrl, String contentType, String fileName) {
    }
}
