package com.company.lms.zoom.repository;

import com.company.lms.zoom.entity.ZoomParticipantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ZoomParticipantRepository extends JpaRepository<ZoomParticipantEntity, UUID> {
    Optional<ZoomParticipantEntity> findTopByZoomMeetingIdFkAndZoomUserIdOrderByCreatedAtDesc(UUID meetingId, String zoomUserId);

    java.util.List<ZoomParticipantEntity> findByZoomMeetingIdFk(UUID meetingId);

    java.util.List<ZoomParticipantEntity> findByZoomMeetingIdFkAndStudentId(UUID meetingId, UUID studentId);
}

