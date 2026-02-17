package com.company.lms.video.repository;

import com.company.lms.video.entity.VideoProgressEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VideoProgressRepository extends JpaRepository<VideoProgressEntity, UUID> {
    Optional<VideoProgressEntity> findByStudentIdAndLessonId(UUID studentId, UUID lessonId);

    List<VideoProgressEntity> findByStudentIdAndLessonIdIn(UUID studentId, Collection<UUID> lessonIds);

    List<VideoProgressEntity> findByStudentIdInAndLessonIdIn(Collection<UUID> studentIds, Collection<UUID> lessonIds);

    @Modifying
    @Query(value = """
            INSERT INTO video_progress (id, student_id, lesson_id, watched_seconds, total_seconds, completion_pct, last_event_at, suspicious_flags, created_at, updated_at)
            VALUES (:id, :studentId, :lessonId, :watchedSeconds, :totalSeconds, :completionPct, :lastEventAt, CAST(:suspiciousFlags AS jsonb), :createdAt, :updatedAt)
            ON CONFLICT (student_id, lesson_id)
            DO UPDATE SET
                watched_seconds = GREATEST(video_progress.watched_seconds, EXCLUDED.watched_seconds),
                total_seconds = CASE WHEN video_progress.total_seconds = 0 THEN EXCLUDED.total_seconds ELSE video_progress.total_seconds END,
                completion_pct = GREATEST(video_progress.completion_pct, EXCLUDED.completion_pct),
                last_event_at = EXCLUDED.last_event_at,
                suspicious_flags = EXCLUDED.suspicious_flags,
                updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    void upsertProgress(
            @Param("id") UUID id,
            @Param("studentId") UUID studentId,
            @Param("lessonId") UUID lessonId,
            @Param("watchedSeconds") int watchedSeconds,
            @Param("totalSeconds") int totalSeconds,
            @Param("completionPct") BigDecimal completionPct,
            @Param("lastEventAt") OffsetDateTime lastEventAt,
            @Param("suspiciousFlags") String suspiciousFlags,
            @Param("createdAt") OffsetDateTime createdAt,
            @Param("updatedAt") OffsetDateTime updatedAt
    );
}
