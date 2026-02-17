package com.company.lms.courses.modules.repository;

import com.company.lms.courses.modules.entity.CourseModuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseModuleRepository extends JpaRepository<CourseModuleEntity, UUID> {
    List<CourseModuleEntity> findByCourseIdOrderBySortOrderAsc(UUID courseId);

    long countByCourseId(UUID courseId);

    Optional<CourseModuleEntity> findByIdAndCourseId(UUID id, UUID courseId);
}
