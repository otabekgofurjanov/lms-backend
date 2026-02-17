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
@Table(name = "zoom_meetings")
@Getter
@Setter
public class ZoomMeetingEntity {
    @Id
    private UUID id;

    @Column(name = "lesson_id")
    private UUID lessonId;

    @Column(name = "zoom_account_id")
    private UUID zoomAccountId;

    @Column(name = "zoom_meeting_id")
    private String zoomMeetingId;

    @Column(name = "join_url")
    private String joinUrl;

    @Column(name = "start_time")
    private OffsetDateTime startTime;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "actual_start_time")
    private OffsetDateTime actualStartTime;

    @Column(name = "actual_end_time")
    private OffsetDateTime actualEndTime;

    private String status;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
