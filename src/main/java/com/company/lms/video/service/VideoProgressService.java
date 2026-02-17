package com.company.lms.video.service;

import com.company.lms.audit.service.AuditService;
import com.company.lms.auth.entity.UserEntity;
import com.company.lms.auth.repository.UserRepository;
import com.company.lms.common.dto.PageResponse;
import com.company.lms.common.exception.AppException;
import com.company.lms.courses.entity.CourseEntity;
import com.company.lms.courses.lessons.entity.LessonEntity;
import com.company.lms.courses.lessons.repository.LessonRepository;
import com.company.lms.courses.modules.entity.CourseModuleEntity;
import com.company.lms.courses.modules.repository.CourseModuleRepository;
import com.company.lms.courses.repository.CourseRepository;
import com.company.lms.courses.service.CourseAccessService;
import com.company.lms.enrollment.entity.EnrollmentEntity;
import com.company.lms.enrollment.repository.EnrollmentRepository;
import com.company.lms.enrollment.service.EnrollmentService;
import com.company.lms.video.dto.*;
import com.company.lms.video.entity.VideoProgressEntity;
import com.company.lms.video.mapper.VideoProgressMapper;
import com.company.lms.video.repository.VideoProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class VideoProgressService {
    private final CourseAccessService courseAccessService;
    private final EnrollmentService enrollmentService;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final CourseModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final VideoProgressRedisService redisService;
    private final VideoProgressRepository videoProgressRepository;
    private final VideoProgressMapper videoProgressMapper;
    private final VideoProgressProperties properties;
    private final AuditService auditService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public VideoSessionResponse createSession(UUID lessonId, String studentEmail) {
        UserEntity student = courseAccessService.requireActor(studentEmail);
        UUID courseId = resolveCourseIdByLesson(lessonId);
        assertStudentEnrollment(student.getId(), courseId);

        UUID sessionId = redisService.createSession(student.getId(), lessonId);
        return new VideoSessionResponse(sessionId, lessonId, OffsetDateTime.now(ZoneOffset.UTC), properties.requiredCompletionPctToUnlockQuiz());
    }

    @Transactional(readOnly = true)
    public VideoProgressUpdateResponse acceptProgress(UUID lessonId, String studentEmail, VideoProgressEventRequest request) {
        UserEntity student = courseAccessService.requireActor(studentEmail);
        UUID courseId = resolveCourseIdByLesson(lessonId);
        assertStudentEnrollment(student.getId(), courseId);

        Map<Object, Object> session = redisService.getSession(request.sessionId());
        if (session.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "VIDEO_SESSION_INVALID", "Video session is not found or expired");
        }
        if (!student.getId().toString().equals(session.get("studentId")) || !lessonId.toString().equals(session.get("lessonId"))) {
            throw new AppException(HttpStatus.FORBIDDEN, "VIDEO_SESSION_FORBIDDEN", "Session does not belong to this student or lesson");
        }

        long prevPing = redisService.getAndUpdateLastPing(request.sessionId(), request.eventTime().toEpochSecond());
        if (prevPing > 0 && request.eventTime().toEpochSecond() - prevPing < properties.progressPingMinIntervalSeconds()) {
            throw new AppException(HttpStatus.TOO_MANY_REQUESTS, "VIDEO_PROGRESS_RATE_LIMITED", "Progress ping too frequent");
        }

        int cappedDelta = Math.min(Math.max(0, request.watchedDeltaSeconds()), 15);
        var current = redisService.getProgress(student.getId(), lessonId);
        if (current != null && current.totalSeconds() > 0 && current.totalSeconds() != request.totalSeconds()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "VIDEO_TOTAL_SECONDS_TAMPER", "totalSeconds mismatch detected");
        }

        var state = redisService.applyDelta(
                student.getId(), lessonId, cappedDelta, request.totalSeconds(), request.eventTime(),
                request.tabSwitchCountDelta(), request.seekAttemptDelta()
        );

        if (isSuspicious(state)) {
            auditService.log(student.getId(), "VIDEO_PROGRESS_SUSPICIOUS", "LESSON", lessonId.toString(), null,
                    videoProgressMapper.suspiciousJson(state), null, null);
        }

        BigDecimal completionPct = videoProgressMapper.calcCompletion(state.watchedSeconds(), state.totalSeconds());
        return new VideoProgressUpdateResponse(true, completionPct, completionPct.compareTo(BigDecimal.valueOf(properties.requiredCompletionPctToUnlockQuiz())) >= 0);
    }

    @Transactional(readOnly = true)
    public VideoProgressSnapshotResponse studentProgress(UUID lessonId, String studentEmail) {
        UserEntity student = courseAccessService.requireActor(studentEmail);
        UUID courseId = resolveCourseIdByLesson(lessonId);
        assertStudentEnrollment(student.getId(), courseId);

        var redis = redisService.getProgress(student.getId(), lessonId);
        if (redis != null) {
            return videoProgressMapper.fromRedis(redis);
        }

        VideoProgressEntity entity = videoProgressRepository.findByStudentIdAndLessonId(student.getId(), lessonId)
                .orElseGet(() -> {
                    VideoProgressEntity e = new VideoProgressEntity();
                    e.setWatchedSeconds(0);
                    e.setTotalSeconds(0);
                    e.setCompletionPct(BigDecimal.ZERO.setScale(2));
                    e.setSuspiciousFlags("{}");
                    return e;
                });
        return videoProgressMapper.fromEntity(entity);
    }

    @Transactional(readOnly = true)
    public CourseVideoProgressReportResponse teacherReport(UUID courseId, String teacherEmail, int page, int size) {
        UserEntity teacher = courseAccessService.requireActor(teacherEmail);
        CourseEntity course = requireCourse(courseId);
        courseAccessService.assertCanManageCourse(teacher, course);
        return buildCourseReport(courseId, page, size);
    }

    @Transactional(readOnly = true)
    public CourseVideoProgressReportResponse adminReport(UUID courseId, String adminEmail, int page, int size) {
        courseAccessService.requireActor(adminEmail);
        requireCourse(courseId);
        return buildCourseReport(courseId, page, size);
    }

    public boolean isLessonCompleted(UUID studentId, UUID lessonId) {
        var redis = redisService.getProgress(studentId, lessonId);
        BigDecimal completion = redis != null
                ? videoProgressMapper.calcCompletion(redis.watchedSeconds(), redis.totalSeconds())
                : videoProgressRepository.findByStudentIdAndLessonId(studentId, lessonId)
                .map(VideoProgressEntity::getCompletionPct)
                .orElse(BigDecimal.ZERO);
        return completion.compareTo(BigDecimal.valueOf(properties.requiredCompletionPctToUnlockQuiz())) >= 0;
    }

    private CourseVideoProgressReportResponse buildCourseReport(UUID courseId, int page, int size) {
        var enrollments = enrollmentRepository.findByCourse(courseId, "ACTIVE", PageRequest.of(page, size));
        List<EnrollmentEntity> list = enrollments.getContent();

        List<UUID> studentIds = list.stream().map(EnrollmentEntity::getStudentId).toList();
        List<UUID> moduleIds = moduleRepository.findByCourseIdOrderBySortOrderAsc(courseId).stream().map(CourseModuleEntity::getId).toList();
        List<UUID> lessonIds = moduleIds.isEmpty() ? List.of() : lessonRepository.findByModuleIdIn(moduleIds).stream().map(LessonEntity::getId).toList();
        List<VideoProgressEntity> progresses = (studentIds.isEmpty() || lessonIds.isEmpty()) ? List.of() : videoProgressRepository.findByStudentIdInAndLessonIdIn(studentIds, lessonIds);

        Map<UUID, List<VideoProgressEntity>> byStudent = new HashMap<>();
        for (VideoProgressEntity p : progresses) {
            byStudent.computeIfAbsent(p.getStudentId(), k -> new ArrayList<>()).add(p);
        }

        List<CourseVideoProgressStudentResponse> rows = new ArrayList<>();
        for (EnrollmentEntity en : list) {
            UUID sid = en.getStudentId();
            List<VideoProgressEntity> sProgress = byStudent.getOrDefault(sid, List.of());
            BigDecimal avg = sProgress.isEmpty() ? BigDecimal.ZERO :
                    sProgress.stream().map(VideoProgressEntity::getCompletionPct).reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(sProgress.size()), 2, java.math.RoundingMode.HALF_UP);
            OffsetDateTime last = sProgress.stream().map(VideoProgressEntity::getLastEventAt).filter(Objects::nonNull).max(OffsetDateTime::compareTo).orElse(null);
            Map<String, Object> suspicious = new HashMap<>();
            int tab = 0;
            int seek = 0;
            for (VideoProgressEntity p : sProgress) {
                try {
                    var map = objectMapper.readValue(p.getSuspiciousFlags(), Map.class);
                    tab += Integer.parseInt(String.valueOf(map.getOrDefault("tabSwitchCount", 0)));
                    seek += Integer.parseInt(String.valueOf(map.getOrDefault("seekAttempts", 0)));
                } catch (Exception ignored) {
                }
            }
            suspicious.put("tabSwitchCount", tab);
            suspicious.put("seekAttempts", seek);
            String fullName = userRepository.findById(sid).map(UserEntity::getFullName).orElse(sid.toString());
            rows.add(new CourseVideoProgressStudentResponse(sid, fullName, avg, last, suspicious));
        }

        BigDecimal overall = rows.isEmpty() ? BigDecimal.ZERO : rows.stream().map(CourseVideoProgressStudentResponse::avgCompletionPct)
                .reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(rows.size()), 2, java.math.RoundingMode.HALF_UP);

        return new CourseVideoProgressReportResponse(overall,
                new PageResponse<>(rows, enrollments.getNumber(), enrollments.getSize(), enrollments.getTotalElements(), enrollments.getTotalPages()));
    }

    private UUID resolveCourseIdByLesson(UUID lessonId) {
        LessonEntity lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "LESSON_NOT_FOUND", "Lesson not found"));
        CourseModuleEntity module = moduleRepository.findById(lesson.getModuleId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "MODULE_NOT_FOUND", "Module not found"));
        return module.getCourseId();
    }

    private void assertStudentEnrollment(UUID studentId, UUID courseId) {
        if (!enrollmentService.hasActiveEnrollment(courseId, studentId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "ENROLLMENT_REQUIRED", "Active enrollment required");
        }
    }

    private boolean isSuspicious(VideoProgressRedisService.ProgressState state) {
        return state.tabSwitchCount() >= properties.suspicious().tabSwitchThreshold()
                || state.seekAttempts() >= properties.suspicious().seekAttemptThreshold();
    }

    private CourseEntity requireCourse(UUID courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "Course not found"));
    }
}
