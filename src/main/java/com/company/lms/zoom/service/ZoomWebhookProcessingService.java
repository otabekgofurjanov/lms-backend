package com.company.lms.zoom.service;

import com.company.lms.audit.service.AuditService;
import com.company.lms.video.service.RecordingIngestService;
import com.company.lms.zoom.entity.ZoomMeetingEntity;
import com.company.lms.zoom.entity.ZoomParticipantEntity;
import com.company.lms.zoom.repository.ZoomMeetingRepository;
import com.company.lms.zoom.repository.ZoomParticipantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ZoomWebhookProcessingService {
    private final ObjectMapper objectMapper;
    private final ZoomMeetingRepository zoomMeetingRepository;
    private final ZoomParticipantRepository zoomParticipantRepository;
    private final AuditService auditService;
    private final RecordingIngestService recordingIngestService;

    @Async
    @Transactional
    public void processWebhookAsync(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String event = root.path("event").asText();
            JsonNode payload = root.path("payload").path("object");
            String zoomMeetingId = payload.path("id").asText(payload.path("uuid").asText());

            if (zoomMeetingId == null || zoomMeetingId.isBlank()) {
                return;
            }

            Optional<ZoomMeetingEntity> meetingOptional = zoomMeetingRepository.findByZoomMeetingId(zoomMeetingId);
            if (meetingOptional.isEmpty()) {
                return;
            }

            ZoomMeetingEntity meeting = meetingOptional.get();
            auditService.log(meeting.getCreatedBy(), "ZOOM_WEBHOOK_RECEIVED", "ZOOM_MEETING", zoomMeetingId, null, "{\"event\":\"" + event + "\"}", null, null);

            if ("meeting.ended".equals(event)) {
                meeting.setStatus("ENDED");
                if (meeting.getActualEndTime() == null) {
                    meeting.setActualEndTime(OffsetDateTime.now());
                }
                zoomMeetingRepository.save(meeting);
                return;
            }

            if ("recording.completed".equals(event)) {
                recordingIngestService.handleZoomRecordingCompleted(meeting, rawBody);
                return;
            }

            JsonNode participantNode = payload.path("participant");
            String zoomUserId = participantNode.path("user_id").asText(null);
            String displayName = participantNode.path("user_name").asText(participantNode.path("name").asText(null));
            OffsetDateTime eventTime = parseTime(participantNode.path("join_time").asText(participantNode.path("leave_time").asText(null)));

            ZoomParticipantEntity participant = findParticipant(meeting.getId(), zoomUserId).orElseGet(() -> {
                ZoomParticipantEntity p = new ZoomParticipantEntity();
                p.setId(UUID.randomUUID());
                p.setZoomMeetingIdFk(meeting.getId());
                p.setCreatedAt(OffsetDateTime.now());
                p.setDurationSeconds(0);
                p.setAttendanceStatus("UNKNOWN");
                return p;
            });

            participant.setZoomUserId(zoomUserId);
            participant.setDisplayName(displayName);
            participant.setRawPayload(rawBody);

            if ("meeting.participant_joined".equals(event)) {
                OffsetDateTime jt = eventTime != null ? eventTime : OffsetDateTime.now();
                participant.setJoinTime(jt);
                if (meeting.getActualStartTime() == null || jt.isBefore(meeting.getActualStartTime())) {
                    meeting.setActualStartTime(jt);
                    zoomMeetingRepository.save(meeting);
                }
                participant.setAttendanceStatus("JOINED");
            } else if ("meeting.participant_left".equals(event)) {
                OffsetDateTime leave = eventTime != null ? eventTime : OffsetDateTime.now();
                if (meeting.getActualEndTime() == null || leave.isAfter(meeting.getActualEndTime())) {
                    meeting.setActualEndTime(leave);
                    zoomMeetingRepository.save(meeting);
                }
                participant.setLeaveTime(leave);
                if (participant.getJoinTime() != null) {
                    participant.setDurationSeconds((int) ChronoUnit.SECONDS.between(participant.getJoinTime(), leave));
                }
                participant.setAttendanceStatus("LEFT");
            }

            zoomParticipantRepository.save(participant);
        } catch (Exception ignored) {
        }
    }

    private Optional<ZoomParticipantEntity> findParticipant(UUID meetingId, String zoomUserId) {
        if (zoomUserId == null) {
            return Optional.empty();
        }
        return zoomParticipantRepository.findTopByZoomMeetingIdFkAndZoomUserIdOrderByCreatedAtDesc(meetingId, zoomUserId);
    }

    private OffsetDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception e) {
            return null;
        }
    }
}
