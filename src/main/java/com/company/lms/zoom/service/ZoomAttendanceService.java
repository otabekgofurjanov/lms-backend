package com.company.lms.zoom.service;

import com.company.lms.auth.entity.UserEntity;
import com.company.lms.auth.repository.UserRepository;
import com.company.lms.common.dto.PageResponse;
import com.company.lms.common.exception.AppException;
import com.company.lms.completion.dto.StudentAttendanceSummaryDto;
import com.company.lms.completion.entity.CourseProgressEntity;
import com.company.lms.completion.repository.CourseProgressRepository;
import com.company.lms.completion.service.AttendanceAggregationService;
import com.company.lms.courses.entity.CourseEntity;
import com.company.lms.courses.lessons.entity.LessonEntity;
import com.company.lms.courses.lessons.repository.LessonRepository;
import com.company.lms.courses.modules.entity.CourseModuleEntity;
import com.company.lms.courses.modules.repository.CourseModuleRepository;
import com.company.lms.courses.service.CourseAccessService;
import com.company.lms.enrollment.repository.EnrollmentRepository;
import com.company.lms.zoom.dto.*;
import com.company.lms.zoom.entity.ZoomMeetingEntity;
import com.company.lms.zoom.entity.ZoomParticipantEntity;
import com.company.lms.zoom.repository.ZoomMeetingRepository;
import com.company.lms.zoom.repository.ZoomParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ZoomAttendanceService {
    private final ZoomIdentityMatchingService matchingService;
    private final AttendanceCalculationService calculationService;
    private final AttendanceAggregationService aggregationService;
    private final ZoomMeetingRepository zoomMeetingRepository;
    private final LessonRepository lessonRepository;
    private final CourseModuleRepository moduleRepository;
    private final com.company.lms.courses.repository.CourseRepository courseRepository;
    private final CourseAccessService accessService;
    private final CourseProgressRepository courseProgressRepository;
    private final UserRepository userRepository;
    private final ZoomParticipantRepository participantRepository;
    private final EnrollmentRepository enrollmentRepository;

    @Transactional
    public AttendanceRecalculateResponse recalculateMeeting(UUID meetingId) {
        ZoomIdentityMatchingService.MatchingSummary m = matchingService.autoMatchMeeting(meetingId);
        AttendanceCalculationService.CalculationSummary c = calculationService.calculateForMeeting(meetingId);
        UUID courseId = courseIdByMeeting(meetingId);
        aggregationService.recalculateForCourse(courseId);
        return new AttendanceRecalculateResponse(meetingId, m.processed(), m.matched(), m.unmatched(), c.updatedStatuses());
    }

    @Transactional
    public CourseAttendanceRecalculateResponse recalculateCourse(UUID courseId) {
        List<ZoomMeetingEntity> meetings = meetingsForCourse(courseId);
        int processed = 0, matched = 0, unmatched = 0, statuses = 0;
        for (ZoomMeetingEntity meeting : meetings) {
            ZoomIdentityMatchingService.MatchingSummary m = matchingService.autoMatchMeeting(meeting.getId());
            AttendanceCalculationService.CalculationSummary c = calculationService.calculateForMeeting(meeting.getId());
            processed += m.processed();
            matched += m.matched();
            unmatched += m.unmatched();
            statuses += c.updatedStatuses();
        }
        aggregationService.recalculateForCourse(courseId);
        return new CourseAttendanceRecalculateResponse(courseId, meetings.size(), processed, matched, unmatched, statuses);
    }

    @Transactional(readOnly = true)
    public TeacherCourseAttendanceResponse teacherReport(UUID courseId, String teacherEmail, int page, int size) {
        UserEntity teacher = accessService.requireActor(teacherEmail);
        CourseEntity course = courseRepository.findById(courseId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "Course not found"));
        accessService.assertCanManageCourse(teacher, course);

        List<CourseProgressEntity> all = courseProgressRepository.findByCourseId(courseId);
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        List<StudentAttendanceSummaryDto> items = all.subList(from, to).stream().map(cp -> {
            UserEntity s = userRepository.findById(cp.getStudentId()).orElseThrow();
            String lastStatus = lastMeetingStatus(courseId, s.getId());
            return new StudentAttendanceSummaryDto(s.getId(), s.getFullName(), cp.getAttendancePct(), lastStatus);
        }).toList();

        PageResponse<StudentAttendanceSummaryDto> pr = new PageResponse<>(items, page, size, all.size(), (int) Math.ceil(all.size() / (double) size));
        return new TeacherCourseAttendanceResponse(courseId, meetingsForCourse(courseId).size(), pr);
    }

    @Transactional(readOnly = true)
    public StudentCourseAttendanceResponse studentReport(UUID courseId, String studentEmail) {
        UserEntity student = accessService.requireActor(studentEmail);
        if (!enrollmentRepository.existsByCourseIdAndStudentIdAndStatus(courseId, student.getId(), "ACTIVE")) {
            throw new AppException(HttpStatus.FORBIDDEN, "ENROLLMENT_REQUIRED", "Active enrollment required");
        }

        BigDecimal pct = courseProgressRepository.findByCourseIdAndStudentId(courseId, student.getId())
                .map(CourseProgressEntity::getAttendancePct).orElse(BigDecimal.ZERO);

        List<StudentMeetingAttendanceDto> meetings = meetingsForCourse(courseId).stream().map(m -> {
            LessonEntity lesson = lessonRepository.findById(m.getLessonId()).orElseThrow();
            List<ZoomParticipantEntity> p = participantRepository.findByZoomMeetingIdFkAndStudentId(m.getId(), student.getId());
            int duration = p.stream().map(x -> Optional.ofNullable(x.getDurationSeconds()).orElse(0)).max(Integer::compareTo).orElse(0);
            String status = p.stream().map(ZoomParticipantEntity::getAttendanceStatus).filter(Objects::nonNull).findFirst().orElse("ABSENT");
            return new StudentMeetingAttendanceDto(m.getId(), lesson.getTitle(), status, duration);
        }).toList();

        return new StudentCourseAttendanceResponse(courseId, pct, meetings);
    }

    @Transactional
    public void manualMatch(UUID participantId, UUID studentId, String actorEmail) {
        matchingService.manualMatch(participantId, studentId, actorEmail);
    }

    private UUID courseIdByMeeting(UUID meetingId) {
        ZoomMeetingEntity meeting = zoomMeetingRepository.findById(meetingId).orElseThrow();
        LessonEntity lesson = lessonRepository.findById(meeting.getLessonId()).orElseThrow();
        CourseModuleEntity module = moduleRepository.findById(lesson.getModuleId()).orElseThrow();
        return module.getCourseId();
    }

    private List<ZoomMeetingEntity> meetingsForCourse(UUID courseId) {
        List<UUID> moduleIds = moduleRepository.findByCourseIdOrderBySortOrderAsc(courseId).stream().map(CourseModuleEntity::getId).toList();
        if (moduleIds.isEmpty()) return List.of();
        List<LessonEntity> lessons = lessonRepository.findByModuleIdIn(moduleIds).stream().filter(l -> "LIVE_ZOOM".equals(l.getLessonType())).toList();
        if (lessons.isEmpty()) return List.of();
        return zoomMeetingRepository.findByLessonIdIn(lessons.stream().map(LessonEntity::getId).toList());
    }

    private String lastMeetingStatus(UUID courseId, UUID studentId) {
        List<ZoomMeetingEntity> meetings = meetingsForCourse(courseId);
        if (meetings.isEmpty()) return "ABSENT";
        return meetings.stream()
                .sorted(Comparator.comparing(ZoomMeetingEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(m -> participantRepository.findByZoomMeetingIdFkAndStudentId(m.getId(), studentId))
                .filter(list -> !list.isEmpty())
                .map(list -> list.get(0).getAttendanceStatus())
                .filter(Objects::nonNull)
                .findFirst().orElse("ABSENT");
    }
}
