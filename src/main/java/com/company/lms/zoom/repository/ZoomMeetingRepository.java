package com.company.lms.zoom.repository;

import com.company.lms.zoom.entity.ZoomMeetingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ZoomMeetingRepository extends JpaRepository<ZoomMeetingEntity, UUID> {
    Optional<ZoomMeetingEntity> findByLessonId(UUID lessonId);

    Optional<ZoomMeetingEntity> findByZoomMeetingId(String zoomMeetingId);

    java.util.List<ZoomMeetingEntity> findByLessonIdIn(java.util.List<UUID> lessonIds);
}

