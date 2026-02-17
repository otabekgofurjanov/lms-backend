package com.company.lms.video.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "lesson_recordings")
@Getter
@Setter
public class LessonRecordingEntity {
    @Id
    private UUID id;

    @Column(name = "lesson_id")
    private UUID lessonId;

    @Column(name = "zoom_recording_id")
    private String zoomRecordingId;

    private String status;

    @Column(name = "recorded_at")
    private OffsetDateTime recordedAt;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
