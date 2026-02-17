package com.company.lms.zoom.service;

import com.company.lms.audit.service.AuditService;
import com.company.lms.auth.entity.UserEntity;
import com.company.lms.auth.repository.UserRepository;
import com.company.lms.common.exception.AppException;
import com.company.lms.courses.entity.CourseEntity;
import com.company.lms.courses.lessons.entity.LessonEntity;
import com.company.lms.courses.lessons.repository.LessonRepository;
import com.company.lms.courses.modules.entity.CourseModuleEntity;
import com.company.lms.courses.modules.repository.CourseModuleRepository;
import com.company.lms.courses.service.CourseAccessService;
import com.company.lms.enrollment.repository.EnrollmentRepository;
import com.company.lms.zoom.entity.ZoomMeetingEntity;
import com.company.lms.zoom.entity.ZoomParticipantEntity;
import com.company.lms.zoom.repository.ZoomMeetingRepository;
import com.company.lms.zoom.repository.ZoomParticipantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ZoomIdentityMatchingService {
    private final ZoomParticipantRepository participantRepository;
    private final ZoomMeetingRepository meetingRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final LessonRepository lessonRepository;
    private final CourseModuleRepository moduleRepository;
    private final com.company.lms.courses.repository.CourseRepository courseRepository;
    private final CourseAccessService accessService;

    @Transactional
    public MatchingSummary autoMatchMeeting(UUID meetingId) {
        ZoomMeetingEntity meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "ZOOM_MEETING_NOT_FOUND", "Zoom meeting not found"));
        UUID courseId = courseIdByMeeting(meeting);

        int matched = 0;
        int unmatched = 0;
        List<ZoomParticipantEntity> participants = participantRepository.findByZoomMeetingIdFk(meetingId);
        for (ZoomParticipantEntity participant : participants) {
            boolean ok = tryMatchParticipant(participant, courseId);
            if (ok) matched++; else unmatched++;
        }
        return new MatchingSummary(participants.size(), matched, unmatched);
    }

    @Transactional
    public void manualMatch(UUID participantId, UUID studentId, String actorEmail) {
        UserEntity actor = accessService.requireActor(actorEmail);
        ZoomParticipantEntity p = participantRepository.findById(participantId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "PARTICIPANT_NOT_FOUND", "Participant not found"));
        ZoomMeetingEntity meeting = meetingRepository.findById(p.getZoomMeetingIdFk())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "ZOOM_MEETING_NOT_FOUND", "Zoom meeting not found"));

        UUID courseId = courseIdByMeeting(meeting);
        CourseEntity course = courseRepository.findById(courseId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "Course not found"));
        accessService.assertCanManageCourse(actor, course);

        UserEntity student = userRepository.findById(studentId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
        validateStudentEnrollment(student, courseId);

        p.setStudentId(studentId);
        p.setMatchedBy("MANUAL");
        p.setMatchedAt(OffsetDateTime.now());
        participantRepository.save(p);
        auditService.log(actor.getId(), "PARTICIPANT_MATCHED", "ZOOM_PARTICIPANT", participantId.toString(), null, "MANUAL", null, null);
    }

    private boolean tryMatchParticipant(ZoomParticipantEntity p, UUID courseId) {
        try {
            JsonNode root = objectMapper.readTree(p.getRawPayload());
            JsonNode participant = root.path("payload").path("object").path("participant");
            String email = participant.path("email").asText(null);
            String display = participant.path("user_name").asText(participant.path("name").asText(null));

            if (email != null && !email.isBlank()) {
                Optional<UserEntity> user = userRepository.findByEmail(email.trim().toLowerCase());
                if (user.isPresent() && isEligibleStudent(user.get(), courseId)) {
                    return applyMatch(p, user.get().getId(), "EMAIL");
                }
            }

            if (display != null && !display.isBlank()) {
                String normalized = normalize(display);
                List<UserEntity> candidates = userRepository.findByFullNameIgnoreCase(display.trim());
                List<UserEntity> filtered = candidates.stream()
                        .filter(u -> normalize(u.getFullName()).equals(normalized))
                        .filter(u -> isEligibleStudent(u, courseId))
                        .toList();
                if (filtered.size() == 1) {
                    return applyMatch(p, filtered.get(0).getId(), "NAME");
                }
            }

            auditService.log(null, "PARTICIPANT_UNMATCHED", "ZOOM_PARTICIPANT", p.getId().toString(), null, "{}", null, null);
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean applyMatch(ZoomParticipantEntity p, UUID studentId, String by) {
        p.setStudentId(studentId);
        p.setMatchedBy(by);
        p.setMatchedAt(OffsetDateTime.now());
        participantRepository.save(p);
        auditService.log(studentId, "PARTICIPANT_MATCHED", "ZOOM_PARTICIPANT", p.getId().toString(), null, by, null, null);
        return true;
    }

    private boolean isEligibleStudent(UserEntity user, UUID courseId) {
        return user.getRoles().stream().anyMatch(r -> "STUDENT".equals(r.getCode()))
                && enrollmentRepository.existsByCourseIdAndStudentIdAndStatus(courseId, user.getId(), "ACTIVE");
    }

    private void validateStudentEnrollment(UserEntity student, UUID courseId) {
        if (!student.getRoles().stream().anyMatch(r -> "STUDENT".equals(r.getCode()))) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_STUDENT", "Only STUDENT can be matched");
        }
        if (!enrollmentRepository.existsByCourseIdAndStudentIdAndStatus(courseId, student.getId(), "ACTIVE")) {
            throw new AppException(HttpStatus.BAD_REQUEST, "ENROLLMENT_REQUIRED", "Student must have ACTIVE enrollment in course");
        }
    }

    private UUID courseIdByMeeting(ZoomMeetingEntity meeting) {
        LessonEntity lesson = lessonRepository.findById(meeting.getLessonId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "LESSON_NOT_FOUND", "Lesson not found"));
        CourseModuleEntity module = moduleRepository.findById(lesson.getModuleId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "MODULE_NOT_FOUND", "Module not found"));
        return module.getCourseId();
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    public record MatchingSummary(int processed, int matched, int unmatched) {}
}
