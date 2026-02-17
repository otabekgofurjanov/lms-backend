package com.company.lms.completion.service;

import com.company.lms.completion.entity.CourseProgressEntity;
import com.company.lms.completion.repository.CourseProgressRepository;
import com.company.lms.courses.lessons.entity.LessonEntity;
import com.company.lms.courses.lessons.repository.LessonRepository;
import com.company.lms.courses.modules.entity.CourseModuleEntity;
import com.company.lms.courses.modules.repository.CourseModuleRepository;
import com.company.lms.enrollment.entity.EnrollmentEntity;
import com.company.lms.enrollment.repository.EnrollmentRepository;
import com.company.lms.zoom.entity.ZoomMeetingEntity;
import com.company.lms.zoom.entity.ZoomParticipantEntity;
import com.company.lms.zoom.repository.ZoomMeetingRepository;
import com.company.lms.zoom.repository.ZoomParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AttendanceAggregationService {
    private final CourseProgressRepository courseProgressRepository;
    private final CourseModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final ZoomMeetingRepository zoomMeetingRepository;
    private final ZoomParticipantRepository participantRepository;
    private final EnrollmentRepository enrollmentRepository;

    @Transactional
    public void recalculateForCourse(UUID courseId) {
        List<CourseModuleEntity> modules = moduleRepository.findByCourseIdOrderBySortOrderAsc(courseId);
        List<UUID> moduleIds = modules.stream().map(CourseModuleEntity::getId).toList();
        if (moduleIds.isEmpty()) {
            updateZeroForEnrollments(courseId);
            return;
        }

        List<LessonEntity> lessons = lessonRepository.findByModuleIdIn(moduleIds).stream()
                .filter(l -> "LIVE_ZOOM".equals(l.getLessonType())).toList();
        if (lessons.isEmpty()) {
            updateZeroForEnrollments(courseId);
            return;
        }

        List<ZoomMeetingEntity> meetings = zoomMeetingRepository.findByLessonIdIn(lessons.stream().map(LessonEntity::getId).toList());
        int totalMeetings = meetings.size();
        List<EnrollmentEntity> enrollments = enrollmentRepository.findByCourseIdAndStatus(courseId, "ACTIVE");

        for (EnrollmentEntity enrollment : enrollments) {
            BigDecimal pct = totalMeetings == 0 ? BigDecimal.ZERO : calculateAverage(meetings, enrollment.getStudentId(), totalMeetings);
            upsertProgress(courseId, enrollment.getStudentId(), pct);
        }
    }

    private BigDecimal calculateAverage(List<ZoomMeetingEntity> meetings, UUID studentId, int totalMeetings) {
        double sum = 0;
        for (ZoomMeetingEntity meeting : meetings) {
            List<ZoomParticipantEntity> participants = participantRepository.findByZoomMeetingIdFkAndStudentId(meeting.getId(), studentId);
            if (participants.isEmpty()) {
                sum += 0;
            } else {
                int maxDuration = participants.stream().map(p -> Optional.ofNullable(p.getDurationSeconds()).orElse(0)).max(Integer::compareTo).orElse(0);
                int meetingSeconds = meetingDurationSeconds(meeting);
                double pct = meetingSeconds <= 0 ? 0 : (maxDuration * 100.0 / meetingSeconds);
                sum += Math.min(100, Math.max(0, pct));
            }
        }
        return BigDecimal.valueOf(sum / totalMeetings).setScale(2, RoundingMode.HALF_UP);
    }

    private int meetingDurationSeconds(ZoomMeetingEntity meeting) {
        if (meeting.getActualStartTime() != null && meeting.getActualEndTime() != null && meeting.getActualEndTime().isAfter(meeting.getActualStartTime())) {
            long actual = java.time.Duration.between(meeting.getActualStartTime(), meeting.getActualEndTime()).getSeconds();
            int planned = Optional.ofNullable(meeting.getDurationMinutes()).orElse(0) * 60;
            return (int) Math.max(actual, planned);
        }
        return Optional.ofNullable(meeting.getDurationMinutes()).orElse(0) * 60;
    }

    private void upsertProgress(UUID courseId, UUID studentId, BigDecimal pct) {
        CourseProgressEntity entity = courseProgressRepository.findByCourseIdAndStudentId(courseId, studentId)
                .orElseGet(() -> {
                    CourseProgressEntity e = new CourseProgressEntity();
                    e.setId(UUID.randomUUID());
                    e.setCourseId(courseId);
                    e.setStudentId(studentId);
                    e.setStatus("IN_PROGRESS");
                    return e;
                });
        entity.setAttendancePct(pct);
        entity.setUpdatedAt(OffsetDateTime.now());
        courseProgressRepository.save(entity);
    }

    private void updateZeroForEnrollments(UUID courseId) {
        for (EnrollmentEntity e : enrollmentRepository.findByCourseIdAndStatus(courseId, "ACTIVE")) {
            upsertProgress(courseId, e.getStudentId(), BigDecimal.ZERO);
        }
    }
}
