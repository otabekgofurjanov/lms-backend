package com.company.lms.video.repository;

import com.company.lms.video.entity.LessonRecordingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LessonRecordingRepository extends JpaRepository<LessonRecordingEntity, UUID> {
    Optional<LessonRecordingEntity> findByLessonId(UUID lessonId);
}
