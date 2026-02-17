package com.company.lms.video.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "video_progress")
@Getter
@Setter
public class VideoProgressEntity {
    @Id
    private UUID id;

    @Column(name = "student_id")
    private UUID studentId;

    @Column(name = "lesson_id")
    private UUID lessonId;

    @Column(name = "watched_seconds")
    private Integer watchedSeconds;

    @Column(name = "total_seconds")
    private Integer totalSeconds;

    @Column(name = "completion_pct")
    private BigDecimal completionPct;

    @Column(name = "last_event_at")
    private OffsetDateTime lastEventAt;

    @Column(name = "suspicious_flags", columnDefinition = "jsonb")
    private String suspiciousFlags;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
