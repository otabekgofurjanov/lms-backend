package com.company.lms.zoom.service;

import com.company.lms.audit.service.AuditService;
import com.company.lms.common.exception.AppException;
import com.company.lms.zoom.entity.ZoomMeetingEntity;
import com.company.lms.zoom.entity.ZoomParticipantEntity;
import com.company.lms.zoom.repository.ZoomMeetingRepository;
import com.company.lms.zoom.repository.ZoomParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttendanceCalculationService {
    private final ZoomMeetingRepository zoomMeetingRepository;
    private final ZoomParticipantRepository participantRepository;
    private final AttendanceProperties attendanceProperties;
    private final AuditService auditService;

    @Transactional
    public CalculationSummary calculateForMeeting(UUID meetingId) {
        ZoomMeetingEntity meeting = zoomMeetingRepository.findById(meetingId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "ZOOM_MEETING_NOT_FOUND", "Zoom meeting not found"));
        List<ZoomParticipantEntity> participants = participantRepository.findByZoomMeetingIdFk(meetingId);

        if (meeting.getActualStartTime() == null) {
            participants.stream().map(ZoomParticipantEntity::getJoinTime).filter(java.util.Objects::nonNull).min(Comparator.naturalOrder()).ifPresent(meeting::setActualStartTime);
        }
        if (meeting.getActualEndTime() == null) {
            participants.stream().map(ZoomParticipantEntity::getLeaveTime).filter(java.util.Objects::nonNull).max(Comparator.naturalOrder()).ifPresent(meeting::setActualEndTime);
        }
        zoomMeetingRepository.save(meeting);

        int totalSeconds = totalMeetingSeconds(meeting);
        int updated = 0;

        for (ZoomParticipantEntity participant : participants) {
            if (participant.getStudentId() == null) {
                continue;
            }
            int duration = Optional.ofNullable(participant.getDurationSeconds()).orElse(0);
            double pct = totalSeconds <= 0 ? 0 : duration * 100.0 / totalSeconds;
            String status = pct >= attendanceProperties.presentThresholdPct() ? "PRESENT"
                    : pct >= attendanceProperties.lateThresholdPct() ? "LATE" : "ABSENT";
            participant.setAttendanceStatus(status);
            participantRepository.save(participant);
            updated++;
        }

        auditService.log(meeting.getCreatedBy(), "ATTENDANCE_CALCULATED", "ZOOM_MEETING", meetingId.toString(), null,
                "{\"updated\":" + updated + "}", null, null);
        return new CalculationSummary(participants.size(), updated, totalSeconds);
    }

    private int totalMeetingSeconds(ZoomMeetingEntity meeting) {
        int planned = Optional.ofNullable(meeting.getDurationMinutes()).orElse(0) * 60;
        if (meeting.getActualStartTime() != null && meeting.getActualEndTime() != null && meeting.getActualEndTime().isAfter(meeting.getActualStartTime())) {
            long actual = java.time.Duration.between(meeting.getActualStartTime(), meeting.getActualEndTime()).getSeconds();
            return (int) Math.max(actual, planned);
        }
        return planned;
    }

    public record CalculationSummary(int processedParticipants, int updatedStatuses, int meetingTotalSeconds) {}
}
