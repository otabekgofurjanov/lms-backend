package com.company.lms.video.service;

import com.company.lms.audit.service.AuditService;
import com.company.lms.auth.entity.UserEntity;
import com.company.lms.common.exception.AppException;
import com.company.lms.courses.entity.CourseEntity;
import com.company.lms.courses.lessons.entity.LessonEntity;
import com.company.lms.courses.lessons.repository.LessonRepository;
import com.company.lms.courses.modules.entity.CourseModuleEntity;
import com.company.lms.courses.modules.repository.CourseModuleRepository;
import com.company.lms.courses.repository.CourseRepository;
import com.company.lms.courses.service.CourseAccessService;
import com.company.lms.enrollment.service.EnrollmentService;
import com.company.lms.video.dto.VideoAccessResponse;
import com.company.lms.video.entity.LessonRecordingEntity;
import com.company.lms.video.entity.VideoAssetEntity;
import com.company.lms.video.mapper.VideoMapper;
import com.company.lms.video.repository.LessonRecordingRepository;
import com.company.lms.video.repository.VideoAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VideoAccessService {
    private final CourseAccessService courseAccessService;
    private final LessonRepository lessonRepository;
    private final CourseModuleRepository moduleRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentService enrollmentService;
    private final LessonRecordingRepository lessonRecordingRepository;
    private final VideoAssetRepository videoAssetRepository;
    private final MinioStorageService minioStorageService;
    private final MinioProperties minioProperties;
    private final VideoMapper videoMapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public VideoAccessResponse studentVideo(UUID lessonId, String studentEmail) {
        UserEntity student = courseAccessService.requireActor(studentEmail);
        UUID courseId = resolveCourseIdByLesson(lessonId);
        if (!enrollmentService.hasActiveEnrollment(courseId, student.getId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "ENROLLMENT_REQUIRED", "Active enrollment is required to access the video");
        }
        return loadResponse(lessonId, student.getId(), true);
    }

    @Transactional(readOnly = true)
    public VideoAccessResponse teacherVideo(UUID lessonId, String teacherEmail) {
        UserEntity teacher = courseAccessService.requireActor(teacherEmail);
        CourseEntity course = resolveCourse(lessonId);
        courseAccessService.assertCanManageCourse(teacher, course);
        return loadResponse(lessonId, teacher.getId(), false);
    }

    @Transactional(readOnly = true)
    public VideoAccessResponse adminVideo(UUID lessonId, String adminEmail) {
        UserEntity admin = courseAccessService.requireActor(adminEmail);
        return loadResponse(lessonId, admin.getId(), false);
    }

    private VideoAccessResponse loadResponse(UUID lessonId, UUID actorId, boolean studentAccess) {
        LessonRecordingEntity recording = lessonRecordingRepository.findByLessonId(lessonId).orElseGet(() -> {
            LessonRecordingEntity pending = new LessonRecordingEntity();
            pending.setLessonId(lessonId);
            pending.setStatus("PENDING");
            return pending;
        });

        VideoAssetEntity asset = null;
        String url = null;
        Long expiry = null;
        if ("READY".equals(recording.getStatus())) {
            asset = videoAssetRepository.findTopByRecordingIdOrderByCreatedAtDesc(recording.getId())
                    .orElseThrow(() -> new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "VIDEO_ASSET_NOT_FOUND", "Recording asset is missing"));
            expiry = Long.valueOf(minioProperties.presignedExpirySeconds());
            url = minioStorageService.getPresignedGetUrl(asset.getBucket(), asset.getObjectKey(), minioProperties.presignedExpirySeconds());
            if (studentAccess) {
                auditService.log(actorId, "VIDEO_ACCESS_GRANTED", "LESSON", lessonId.toString(), null, "{}", null, null);
            }
        }
        return videoMapper.toResponse(recording, asset, url, expiry);
    }

    private UUID resolveCourseIdByLesson(UUID lessonId) {
        LessonEntity lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "LESSON_NOT_FOUND", "Lesson not found"));
        CourseModuleEntity module = moduleRepository.findById(lesson.getModuleId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "MODULE_NOT_FOUND", "Module not found"));
        return module.getCourseId();
    }

    private CourseEntity resolveCourse(UUID lessonId) {
        UUID courseId = resolveCourseIdByLesson(lessonId);
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "Course not found"));
    }
}
