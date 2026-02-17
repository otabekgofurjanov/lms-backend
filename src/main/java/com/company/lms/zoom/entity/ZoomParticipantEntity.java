package com.company.lms.zoom.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "zoom_participants")
@Getter
@Setter
public class ZoomParticipantEntity {
    @Id
    private UUID id;

    @Column(name = "zoom_meeting_id_fk")
    private UUID zoomMeetingIdFk;

    @Column(name = "student_id")
    private UUID studentId;

    @Column(name = "zoom_user_id")
    private String zoomUserId;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "join_time")
    private OffsetDateTime joinTime;

    @Column(name = "leave_time")
    private OffsetDateTime leaveTime;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "attendance_status")
    private String attendanceStatus;

    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    @Column(name = "matched_by")
    private String matchedBy;

    @Column(name = "matched_at")
    private OffsetDateTime matchedAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
