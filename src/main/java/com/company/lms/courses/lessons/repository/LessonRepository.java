package com.company.lms.courses.lessons.repository;

import com.company.lms.courses.lessons.entity.LessonEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LessonRepository extends JpaRepository<LessonEntity, UUID> {
    List<LessonEntity> findByModuleIdOrderBySortOrderAsc(UUID moduleId);

    long countByModuleId(UUID moduleId);

    java.util.List<LessonEntity> findByModuleIdIn(java.util.List<UUID> moduleIds);
}

